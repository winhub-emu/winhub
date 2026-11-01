package com.winhub.hernandez.store.download

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.winhub.hernandez.ui.components.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared, theme-token-driven building blocks for the per-store game DETAIL pages
 * (Amazon / Epic / GOG). These mirror the clean layout of Steam's
 * `SteamGameDetailScreen` — a hero-image header, flat info section, one honest
 * progress bar and M3 action buttons — so every store reads the same and honours
 * the active theme preset.
 *
 * Everything here draws from [MaterialTheme.colorScheme] / typography. The ONLY
 * hardcoded colours are the semantic installed-green ([INSTALLED_GREEN]) and the
 * per-store brand accents supplied by [StoreStyle] (used only for the small
 * [StoreBadge] identity pill).
 *
 * Steam keeps its own copies of these pieces inside SteamGameDetailActivity — do
 * not route Steam through here (avoid any Steam regression). This file is the
 * target for the non-Steam stores.
 */

/** Semantic "installed" green, shared with Steam's detail screen. */
val INSTALLED_GREEN = Color(0xFF4CAF50)

/** Coarse install state used to colour [StoreStatusText], mirroring Steam. */
enum class StoreDetailState { INSTALLED, FAILED, DEFAULT }

/**
 * Detail-page header row: back button + weighted spacer + an optional per-store
 * badge slot + trailing [actions] (callers typically pass `{ DownloadsButton() }`,
 * and may prepend a status pill). Matches Steam's header padding (h8 / v4).
 */
@Composable
fun StoreDetailHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    storeBadge: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.weight(1f))
        storeBadge()
        actions()
    }
}

/**
 * Small brand-accent identity pill (e.g. "Amazon" orange). The accent comes from
 * [StoreStyle.accent]; text sits on a translucent tint of it so it works in both
 * light and dark themes without a hardcoded background.
 */
@Composable
fun StoreBadge(store: Store, modifier: Modifier = Modifier) {
    val accent = StoreStyle.accent(store)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = StoreStyle.label(store),
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Hero image box with the subtle bottom fade into the page background, matching
 * Steam. The caller supplies [imageContent] (its own AsyncImage / Image / loader)
 * inside a centred [BoxScope]; the gradient overlay is drawn on top.
 */
@Composable
fun StoreHero(
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    imageContent: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxWidth().height(height),
        contentAlignment = Alignment.Center,
    ) {
        imageContent()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                    ),
                ),
        )
    }
}

/** Small rounded metadata chip (type / size / developer …). Replicated from Steam. */
@Composable
fun InfoChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Status line coloured by install state, exactly like Steam: INSTALLED → green,
 * FAILED → error, otherwise the neutral onSurfaceVariant.
 */
@Composable
fun StoreStatusText(
    text: String,
    state: StoreDetailState = StoreDetailState.DEFAULT,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = when (state) {
            StoreDetailState.INSTALLED -> INSTALLED_GREEN
            StoreDetailState.FAILED    -> MaterialTheme.colorScheme.error
            StoreDetailState.DEFAULT   -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier,
    )
}

/**
 * One honest single-fill progress bar (surfaceVariant track, primary fill) plus an
 * optional label underneath. Pass [label] for a free-form line (file name, %), or
 * [installDone]/[installTotal] (bytes) to render an auto-formatted "X / Y" line.
 * [label] wins when both are supplied.
 */
@Composable
fun StoreProgressBar(
    pct: Int,
    modifier: Modifier = Modifier,
    installDone: Long = -1L,
    installTotal: Long = -1L,
    label: String? = null,
) {
    val frac = (pct / 100f).coerceIn(0f, 1f)
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(frac)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        val labelText = label ?: if (installTotal > 0L && installDone >= 0L) {
            "${formatBytes(installDone)} / ${formatBytes(installTotal)}"
        } else null
        if (labelText != null) {
            Text(
                text = labelText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * M3 action button, RoundedCornerShape(10). [destructive] → error colours
 * (Cancel / Uninstall), otherwise the default primary. Caller applies weight via
 * [modifier] inside a [StoreActionRow].
 */
@Composable
fun StoreActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = if (destructive) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
    ) { Text(text, maxLines = 1) }
}

/**
 * Row that lays weighted action buttons out like Steam (h-pad 16, 8dp gaps).
 * Callers place [StoreActionButton]s with `Modifier.weight(1f)` inside.
 */
@Composable
fun StoreActionRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/**
 * Themed titled section container for a store's extra content (Updates / DLC /
 * Cloud Saves) — the theme-token analog of Steam's Goldberg section styling
 * (surfaceVariant, rounded 12, 16dp inset). Replaces the hardcoded-colour Cards.
 */
@Composable
fun StoreSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/** GB / MB / KB formatter shared by the store detail components. */
internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L     -> "%.1f MB".format(bytes / 1_048_576.0)
    else                    -> "%.0f KB".format(bytes / 1024.0)
}
