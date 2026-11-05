package com.winhub.hernandez.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The "OpenGL" card shared by the container editor (plain switches) and the game editor (Auto | On |
 * Off pills): Fast OpenGL, Show OpenGL FPS and OpenGL vsync off (core.FastOpenGL) as three compact
 * rows under one title and one "?" (R.string.help_opengl_settings). Only the layout lives here; the
 * hosts keep the state, the lock reasons and the D-pad wiring.
 */

/** The game editor's pill values: Auto (follow the container), force on, force off (the shortcut extra). */
val OPENGL_OVERRIDES: List<String> = listOf("", "1", "0")

val OPENGL_FAST_ICON: ImageVector = Icons.Filled.Bolt
val OPENGL_FPS_ICON: ImageVector = Icons.Filled.Speed
val OPENGL_VSYNC_ICON: ImageVector = Icons.Filled.LockOpen

/** The Fast row's small inline note for a FastOpenGL lock reason (null = none). */
fun openGLFastNote(reason: String?): String? = when (reason) {
    null -> null
    com.winhub.hernandez.core.FastOpenGL.NEEDS_TURNIP -> "needs Turnip driver"
    com.winhub.hernandez.core.FastOpenGL.NOT_BUNDLED -> "not in this build"
    else -> reason.replaceFirstChar { it.lowercase() }
}

/** "→ Fast · FPS · Vsync off" / "→ all off": what the game will actually get. */
fun openGLSummary(fast: Boolean, fps: Boolean, vsyncOff: Boolean): String {
    val on = listOfNotNull("Fast".takeIf { fast }, "FPS".takeIf { fps }, "Vsync off".takeIf { vsyncOff })
    return if (on.isEmpty()) "→ all off" else "→ " + on.joinToString(" · ")
}

/**
 * The card frame: "OpenGL", one "?" ([helpFocused] rings it for a D-pad host) and, for the game
 * editor, a right-aligned [summary]. [content] is the rows ([OpenGLCardRow]).
 */
@Composable
fun OpenGLCard(
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    helpFocused: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, cs.primary.copy(alpha = 0.33f), RoundedCornerShape(12.dp)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 2.dp),
        ) {
            Text("OpenGL", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
            IconButton(
                onClick = onHelp,
                modifier = Modifier
                    .size(40.dp)
                    .then(if (helpFocused) Modifier.border(2.dp, cs.primary, CircleShape) else Modifier),
            ) {
                Icon(Icons.Default.Help, contentDescription = "What are these OpenGL settings?", modifier = Modifier.size(18.dp))
            }
            if (summary != null) {
                Text(
                    summary,
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
        content()
    }
}

/**
 * One ~44–48dp row: a thin divider above, a small icon, [label], an optional small inline [note]
 * (the lock or the reason) and the [control] on the right. [focused] draws the D-pad highlight.
 */
@Composable
fun OpenGLCardRow(
    icon: ImageVector,
    label: String,
    note: String?,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    control: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    HorizontalDivider(thickness = 1.dp, color = cs.outlineVariant.copy(alpha = 0.6f))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .then(if (focused) Modifier.background(cs.primary.copy(alpha = 0.12f)) else Modifier)
            .padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        Icon(icon, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 14.sp, color = cs.onSurface)
        Text(
            note ?: "",
            fontSize = 11.sp,
            color = cs.onSurfaceVariant,
            maxLines = 2,
            lineHeight = 13.sp,
            modifier = Modifier.weight(1f).padding(start = 6.dp, end = 6.dp),
        )
        control()
    }
}

/**
 * The game editor's compact pills: `Auto (On|Off) | On | Off`. [autoOn] is what Auto resolves to
 * (the container's switch). [locked] → only Auto is pickable; the stored
 * pick still shows.
 */
@Composable
fun OpenGLPills(selected: String, autoOn: Boolean, locked: Boolean, onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .background(cs.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(2.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (value in OPENGL_OVERRIDES) {
            val on = value == selected
            val enabled = !locked || value.isEmpty()
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(30.dp)
                    .widthIn(min = 40.dp)
                    .background(if (on) cs.primary else Color.Transparent, RoundedCornerShape(50))
                    .selectable(
                        selected = on,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { if (!on) onPick(value) },
                    )
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    when (value) { "1" -> "On"; "0" -> "Off"; else -> "Auto (" + (if (autoOn) "On" else "Off") + ")" },
                    fontSize = 12.sp,
                    fontWeight = if (value.isEmpty()) FontWeight.Medium else FontWeight.SemiBold,
                    color = when {
                        on -> cs.onPrimary
                        enabled -> cs.onSurface
                        else -> cs.onSurface.copy(alpha = 0.38f)
                    },
                )
            }
        }
    }
}
