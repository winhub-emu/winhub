package com.winhub.hernandez.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import com.winhub.hernandez.R
import com.winhub.hernandez.store.GoldbergMode
import com.winhub.hernandez.ui.deck.XmbGlassTheme
import com.winhub.hernandez.ui.deck.deckFocusRing
import com.winhub.hernandez.ui.deck.deckShape
import com.winhub.hernandez.ui.deck.xmbPanel
import com.winhub.hernandez.ui.deck.xmbPopCard
import com.winhub.hernandez.ui.deck.xmbPopFill

// The Deck launch menu in landscape: the methods on the left, then what the picked one is and its own settings, then the options
// every method shares with Cancel / Launch. On the XMB glass in the Glass look, plain Deck surfaces in the Material look.

private class LaunchOption(val mode: String, val title: String, val detail: String, @DrawableRes val icon: Int)

private val LAUNCH_OPTIONS = listOf(
    LaunchOption(
        "RealSteam", "SteamLite",
        "Runs the real Steam client inside the container, so achievements, cloud saves, online play and VAC checks work. Needs a Steam sign-in.",
        R.drawable.logo_steam,
    ),
    LaunchOption(
        "Goldberg", "Goldberg",
        "A stand-in Steam that needs no sign-in. Offline only: no online play and no VAC. Pick how it patches the game.",
        R.drawable.ic_launch_goldberg,
    ),
    LaunchOption(
        "Raw", "Raw .exe",
        "Starts the game's own program directly, with no Steam layer at all.",
        R.drawable.ic_launch_raw,
    ),
)

/** The custom icon of a launch method (by its mode), shared with the portrait menu. */
@DrawableRes
internal fun launchMethodIcon(mode: String): Int = (LAUNCH_OPTIONS.firstOrNull { it.mode == mode } ?: LAUNCH_OPTIONS.last()).icon

private class GoldbergChoice(val mode: GoldbergMode, val name: String, val sub: String)

private val GOLDBERG_CHOICES = listOf(
    GoldbergChoice(GoldbergMode.REGULAR, "Regular", "Standard emulation, best compatibility"),
    GoldbergChoice(GoldbergMode.EXPERIMENTAL, "Experimental", "Newer features, for games Regular can't run"),
    GoldbergChoice(GoldbergMode.COLDCLIENT, "ColdClient", "Runs the game's own launcher, heaviest option"),
)

@Composable
internal fun DeckLaunchLandscapeCard(
    gameName: String,
    heading: String,
    confirmLabel: String,
    method: String,
    onMethod: (String) -> Unit,
    goldbergMode: GoldbergMode,
    onGoldbergMode: (GoldbergMode) -> Unit,
    rememberChoice: Boolean,
    onRemember: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    XmbGlassTheme(enabled = rememberGlassLook()) {
        val cs = MaterialTheme.colorScheme
        val accent = cs.primary
        val shape = deckShape(22.dp)
        val buttonShape = RoundedCornerShape(12.dp)
        val current = LAUNCH_OPTIONS.firstOrNull { it.mode == method } ?: LAUNCH_OPTIONS.last()
        // One requester per row, all attached for good: moving the selection must not rebuild the rows' modifier chains (focus would drop).
        val requesters = remember { List(LAUNCH_OPTIONS.size) { FocusRequester() } }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            val start = LAUNCH_OPTIONS.indexOfFirst { it.mode == method }.coerceAtLeast(0)
            runCatching { requesters[start].requestFocus() }
        }
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 860.dp)
                .fillMaxHeight(0.86f)
                .heightIn(max = 420.dp)
                .xmbPanel(shape, cs.surface)
                .deckDialogNav(onDismiss),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(gameName, color = cs.onSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(heading, color = cs.onSurfaceVariant, fontSize = 13.sp)
                }
                Text("A Select    B Back", color = cs.onSurfaceVariant, fontSize = 13.sp)
            }
            HorizontalDivider(color = cs.outline.copy(alpha = 0.5f))
            Row(Modifier.weight(1f).fillMaxWidth()) {
                // Left: the methods. Moving onto one picks it, so the middle column follows the D-pad.
                Column(
                    Modifier.weight(0.27f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LaunchLabel("Launch with")
                    LAUNCH_OPTIONS.forEachIndexed { i, o ->
                        val selected = o.mode == method
                        LaunchChoiceRow(
                            title = o.title,
                            subtitle = null,
                            selected = selected,
                            accent = accent,
                            modifier = Modifier.focusRequester(requesters[i]),
                            onFocused = { onMethod(o.mode) },
                            onClick = { onMethod(o.mode) },
                            leading = {
                                Icon(
                                    painterResource(o.icon),
                                    contentDescription = null,
                                    tint = if (selected) accent else cs.onSurface,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            trailing = {
                                if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = accent, modifier = Modifier.size(18.dp))
                            },
                        )
                    }
                }
                VerticalDivider(color = cs.outline.copy(alpha = 0.5f))
                // Middle: what the picked method is, then its own settings.
                Column(
                    Modifier.weight(0.40f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LaunchLabel(current.title)
                    Text(current.detail, color = cs.onSurface, fontSize = 14.sp, lineHeight = 20.sp)
                    if (current.mode == "Goldberg") {
                        Spacer(Modifier.height(4.dp))
                        LaunchLabel("Goldberg mode")
                        GOLDBERG_CHOICES.forEach { g ->
                            val selected = g.mode == goldbergMode
                            LaunchChoiceRow(
                                title = g.name,
                                subtitle = g.sub,
                                selected = selected,
                                accent = accent,
                                onClick = { onGoldbergMode(g.mode) },
                                trailing = { RadioButton(selected = selected, onClick = null) },
                            )
                        }
                    }
                }
                VerticalDivider(color = cs.outline.copy(alpha = 0.5f))
                // Right: the options every method shares, Launch and Cancel at the bottom.
                Column(
                    Modifier.weight(0.33f).fillMaxHeight().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LaunchLabel("Options")
                    LaunchChoiceRow(
                        title = "Remember my choice",
                        subtitle = "Skip this menu next time for $gameName.",
                        selected = rememberChoice,
                        accent = accent,
                        onClick = { onRemember(!rememberChoice) },
                        trailing = { Switch(checked = rememberChoice, onCheckedChange = null) },
                    )
                    Spacer(Modifier.weight(1f))
                    HubGradientButton(confirmLabel, Modifier.fillMaxWidth(), onClick = onConfirm)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .deckFocusRing(buttonShape, 1f)
                            .xmbPopCard(buttonShape, xmbPopFill(), shadow = false)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Cancel", color = cs.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun LaunchLabel(text: String) {
    Text(
        text.uppercase(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
    )
}

/** A focusable raised row: [leading] icon, title (+ [subtitle]), [trailing] control. A selected one is tinted and outlined in the accent. */
@Composable
private fun LaunchChoiceRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val fill = xmbPopFill(tint = if (selected) accent else null, tintAlpha = if (selected) 0.16f else 0f)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .deckFocusRing(shape, 1f, onFocused)
            .xmbPopCard(shape, fill, shadow = false)
            .border(1.dp, if (selected) accent else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.size(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = if (selected) accent else cs.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = cs.onSurfaceVariant, fontSize = 12.5.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.size(8.dp))
        trailing()
    }
}
