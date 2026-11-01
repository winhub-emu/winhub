package com.winhub.hernandez.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.annotation.DrawableRes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One tappable entry in a [CollapsibleRail] (a container tab, a file-manager location, …). */
data class RailItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    /** >0 shows a small accent count badge on the item's icon (expanded and collapsed). */
    val badge: Int = 0,
    // onClick stays LAST so existing call sites can pass it as a trailing lambda; badge is an
    // optional named param before it (default 0).
    val onClick: () -> Unit,
)

/** A group of [RailItem]s under an optional small section header (STORAGE / QUICK / FAVORITES …). */
data class RailSection(val header: String?, val items: List<RailItem>)

/**
 * The tiny under-icon label for a COLLAPSED rail item. The rail width never changes, so labels must
 * fit the narrow (~58dp) column: multi-word names wrap to a second line; single words too long to
 * fit are abbreviated; short ones pass through. (Explicit abbreviations first, then a length guard.)
 */
private fun collapsedLabel(label: String): String {
    when (label) {
        "ENVIROMENT" -> return "ENVIROMENT"   // full word; skip the length guard below
        "WIN COMPONENTS" -> return "WIN COMP"
        "Downloads" -> return "Downlds"
    }
    if (label.contains(' ')) return label       // multi-word → wraps to 2 lines
    return if (label.length > 9) label.take(8) + "…" else label
}

/** A rail item's 20dp icon, with a small accent count badge on its corner when [badge] > 0. */
@Composable
private fun RailItemIcon(item: RailItem, tint: androidx.compose.ui.graphics.Color) {
    if (item.badge > 0) {
        BadgedBox(
            badge = {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Text(if (item.badge > 9) "9+" else item.badge.toString(), fontSize = 8.sp) }
            },
        ) {
            Icon(item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(20.dp))
        }
    } else {
        Icon(item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** A secondary link shown under the header (e.g. "What is all this?", "Reset to app defaults"). */
data class RailLink(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * State for a [CollapsibleRail], with the app-wide unified rule:
 *  - PORTRAIT → always collapsed (icon-only), the toggle is HIDDEN and it can never expand.
 *  - LANDSCAPE → expanded by default; the user's collapse choice is remembered PER-SCREEN
 *    ([screenKey]) and is NOT overridden by rotation. portrait→landscape restores that choice.
 *
 * Backed by rememberSaveable (survives process death) and by the fact the app UI activity keeps
 * orientation in configChanges (no recreate) so the selected item / fields / scroll are preserved.
 */
class RailState internal constructor(
    val collapsed: Boolean,
    /** True in portrait: the rail is locked collapsed and must not show an expand toggle. */
    val portraitLocked: Boolean,
    val onToggle: () -> Unit,
)

@Composable
fun rememberRailState(screenKey: String): RailState {
    val context = LocalContext.current
    val prefs = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val choseKey = "rail_${screenKey}_userChose"
    val collapsedKey = "rail_${screenKey}_collapsed"

    // Keep the landscape choice in saveable state regardless of orientation so portrait→landscape
    // restores it. In portrait we simply report collapsed+locked and ignore it.
    var userChose by rememberSaveable(screenKey) { mutableStateOf(prefs.getBoolean(choseKey, false)) }
    var landscapeCollapsed by rememberSaveable(screenKey) {
        mutableStateOf(if (prefs.getBoolean(choseKey, false)) prefs.getBoolean(collapsedKey, false) else false)
    }

    if (!isLandscape) {
        return RailState(collapsed = true, portraitLocked = true, onToggle = {})
    }
    return RailState(
        collapsed = landscapeCollapsed,
        portraitLocked = false,
        onToggle = {
            landscapeCollapsed = !landscapeCollapsed
            userChose = true
            prefs.edit()
                .putBoolean(choseKey, true)
                .putBoolean(collapsedKey, landscapeCollapsed)
                .apply()
        },
    )
}

/**
 * The shared collapsible left rail (mockup "Option 3"): app glyph + title, optional links, and the
 * navigation items, replacing a top tab bar so content runs full height beside it. Width animates
 * 190dp ↔ 58dp. When collapsed it is icon-only (the caller surfaces the active item's name over the
 * content). Used by the container editors, File Manager and Save Manager.
 *
 * [footer] is an optional slot pinned to the bottom of the rail (a weighted spacer pushes it down),
 * for a persistent per-screen summary (e.g. the Save Manager's "N games need syncing" line). The
 * caller renders whatever it wants there and can key it off [RailState.collapsed] to go icon-only.
 */
@Composable
fun CollapsibleRail(
    state: RailState,
    title: String,
    sections: List<RailSection>,
    modifier: Modifier = Modifier,
    links: List<RailLink> = emptyList(),
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    // Optional header glyph (a themed drawable). When null the rail shows its plain solid square.
    // Used by the container editors to show the wine-glass container icon; other rails leave it null.
    @DrawableRes headerIcon: Int? = null,
    // When true each item gets a rounded outlined-button look (the selected one takes the accent
    // border + accent-dim fill), matching the File Manager's "New Folder" button. Off by default so
    // the container/save rails keep their lighter flat rows (5 stacked outlined tabs read too heavy).
    outlinedItems: Boolean = false,
) {
    val collapsed = state.collapsed
    val width by animateDpAsState(if (collapsed) 58.dp else 190.dp, label = "railWidth")

    Column(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        // ── Header: app glyph + title + collapse handle (handle hidden when portrait-locked) ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (collapsed) 0.dp else 10.dp, vertical = 8.dp)
        ) {
            if (headerIcon != null) {
                Icon(
                    painter = painterResource(headerIcon),
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            if (!collapsed) {
                Spacer(Modifier.width(9.dp))
                Text(
                    title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!state.portraitLocked) {
                    IconButton(onClick = state.onToggle, modifier = Modifier.size(28.dp)) {
                        Text("‹‹", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                    }
                }
            }
        }
        // Collapsed (landscape only) → an expand handle; portrait is locked with no handle.
        if (collapsed && !state.portraitLocked) {
            IconButton(onClick = state.onToggle, modifier = Modifier.fillMaxWidth().height(24.dp)) {
                Text("››", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
            }
        }

        // ── Links ──
        if (links.isNotEmpty()) {
            if (collapsed) {
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    links.forEach { link ->
                        IconButton(onClick = link.onClick, modifier = Modifier.size(32.dp)) {
                            Icon(link.icon, link.label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            } else {
                links.forEach { link ->
                    TextButton(onClick = link.onClick, modifier = Modifier.padding(start = 4.dp)) {
                        Icon(link.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(link.label, fontSize = 11.sp)
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

        // ── Sections + items (SCROLLABLE so a tall rail — e.g. many File Manager favourites — is
        //    fully reachable in both orientations; the header above and footer below stay pinned) ──
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
        sections.forEach { section ->
            if (!collapsed && section.header != null) {
                Text(
                    section.header,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 2.dp),
                )
            } else if (collapsed && section.header != null && section != sections.first()) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            section.items.forEach { item ->
                val active = item.selected
                // Outlined variant: each item is a rounded outlined button; selected = accent border +
                // accent-dim fill. Flat variant (default): a simple accent-tinted highlight for selected.
                val shape = RoundedCornerShape(9.dp)
                val base = Modifier.fillMaxWidth()
                val styled = if (outlinedItems) {
                    base
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                        .clip(shape)
                        .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                        .border(
                            1.dp,
                            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            shape,
                        )
                        .clickable(onClick = item.onClick)
                        .padding(horizontal = if (collapsed) 2.dp else 11.dp, vertical = if (collapsed) 6.dp else 9.dp)
                } else {
                    base
                        .clickable(onClick = item.onClick)
                        .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                        .padding(horizontal = if (collapsed) 2.dp else 13.dp, vertical = if (collapsed) 7.dp else 11.dp)
                }
                val iconTint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                val labelColor = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                if (collapsed) {
                    // Icon-only width is unchanged — a TINY label goes UNDER the icon (2 lines max,
                    // centre-aligned, wrapping/abbreviated) so every button stays identifiable without
                    // widening the rail.
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = styled,
                    ) {
                        RailItemIcon(item, iconTint)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            collapsedLabel(item.label),
                            color = labelColor,
                            fontSize = 8.sp,
                            lineHeight = 9.5.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                        modifier = styled,
                    ) {
                        RailItemIcon(item, iconTint)
                        Spacer(Modifier.width(11.dp))
                        Text(
                            item.label,
                            color = labelColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        } // end scrollable section list (its weight fills the gap so the footer stays pinned)

        // ── Optional pinned footer (e.g. the sync summary) ──
        if (footer != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            footer()
        }
    }
}

/**
 * Portrait counterpart to [CollapsibleRail]: the same navigation [items] laid out as a horizontal
 * icon-over-label bar pinned across the top, with an accent underline under the selected one. Mirrors
 * the container editor's top tab bar so every editor reads the same — pin this in portrait wherever a
 * [CollapsibleRail] is used in landscape. Optional [links] (Help / Reset …) are appended after a
 * faint separator, exactly like the rail's links.
 *
 * (The container editor still carries its own private copy of this bar; new screens should prefer
 * this shared one, and the container editor can migrate to it later.)
 */
@Composable
fun RailTopTabs(
    items: List<RailItem>,
    modifier: Modifier = Modifier,
    links: List<RailLink> = emptyList(),
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                TopTabCell(
                    label = topTabLabel(item.label),
                    selected = item.selected,
                    onClick = item.onClick,
                    modifier = Modifier.weight(1f),
                )
            }
            if (links.isNotEmpty()) {
                // Faint separator: everything past here is an action button, not a tab.
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .width(1.dp)
                        .height(30.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                )
                links.forEach { link ->
                    TopTabCell(link.label, selected = false, onClick = link.onClick, modifier = Modifier.weight(1f))
                }
            }
        }
        HorizontalDivider()
    }
}

/** Uppercase tab title for the horizontal strip, with the long ones abbreviated so five or six
 *  cells still fit a phone width without ellipsizing (same rule as [collapsedLabel]). */
private fun topTabLabel(label: String): String = when (label) {
    "Win Components" -> "WIN COMP"
    "Env Vars" -> "ENV VARS"
    "Controller" -> "CTRL"
    else -> label.uppercase()
}

/** One cell of [RailTopTabs] — TEXT ONLY (no icons, by request): the uppercase title on one line
 *  with an accent underline under the selected one (zero-width when unselected, so every cell keeps
 *  the same height). Mirrors the container editor's TopCell. */
@Composable
private fun TopTabCell(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 2.dp),
    ) {
        Text(
            label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .height(2.dp)
                .width(if (selected) 18.dp else 0.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}
