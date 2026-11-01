package com.winhub.hernandez.ui

import androidx.compose.foundation.background
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Right-hand end of the header hairline — the Synthwave primary, so the rail reads purple → magenta. */
private val HairlineBlue = Color(0xFF4FB2FF)

/** Header underline weight: 1dp is a sub-pixel hairline on mdpi panels, 2dp stops reading "thin". */
private val HairlineThickness = 1.5f.dp

/**
 * The app's shared top header band (glyph/nav + screen title + optional actions), used across every
 * app-UI screen. Deliberately SLIM: a compact 40dp Row rather than the Material3 TopAppBar's fixed
 * 64dp, reclaiming ~40% of the header's vertical space (it matters most in landscape).
 */
@Composable
fun AppTopBar(
    title: String,
    showBack: Boolean = false,
    onNavClick: () -> Unit,
    // PHASE 3 (optional accounts): when signed in WITH an avatar, the ☰ is swapped for the user's picture.
    // Tapping it still runs [onNavClick] (opens the drawer) exactly like the hamburger. Null = normal ☰.
    avatarUrl: String? = null,
    // Optional slot rendered immediately to the RIGHT of the title text (left side of the bar), before
    // the flexible gap that pushes [actions] to the far right. Used for the Steam connection pill on
    // the Games screen. Null = title only.
    titleTrailing: (@Composable () -> Unit)? = null,
    // See-through band (the Games tab's XMB view draws its backdrop behind it).
    transparent: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    // Captured here: MaterialTheme.colorScheme is a @Composable getter, so it cannot be read from
    // the drawBehind lambda below.
    val accent = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (transparent) Color.Transparent else MaterialTheme.colorScheme.surface)
            // Azure hairline separating the header band from the screen below: blue → lighter blue,
            // tips faded so it reads as a rail rather than a cut. Drawn, not laid out, so the band
            // keeps its 40dp height and `transparent` (the XMB see-through bar) is left untouched.
            .drawBehind {
                if (!transparent) {
                    val h = HairlineThickness.toPx()
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to accent.copy(alpha = 0f),
                            0.10f to accent,
                            0.90f to HairlineBlue,
                            1f to HairlineBlue.copy(alpha = 0f),
                        ),
                        topLeft = Offset(0f, size.height - h),
                        size = Size(size.width, h),
                    )
                }
            }
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onNavClick),
            contentAlignment = Alignment.Center,
        ) {
            when {
                showBack -> Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                avatarUrl != null -> AccountAvatar(
                    avatarUrl = avatarUrl,
                    size = 28.dp,
                    modifier = Modifier.semantics { contentDescription = "Open menu" },
                )
                else -> Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "Open menu",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.width(2.dp))
        // Title + optional trailing slot occupy the flexible middle (this inner Row takes weight 1f);
        // the title shrinks/ellipsizes (weight, fill=false) to make room for the trailing content, which
        // sits right after it (left-aligned). The inner Row filling the gap keeps [actions] at the far right.
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (titleTrailing != null) {
                Spacer(Modifier.width(8.dp))
                titleTrailing()
            }
        }
        actions()
    }
}
