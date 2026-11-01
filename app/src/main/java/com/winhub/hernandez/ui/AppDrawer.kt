package com.winhub.hernandez.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import com.winhub.hernandez.ui.screens.OutlinedAlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.R
import com.winhub.hernandez.communityconfigs.AccountManager
import com.winhub.hernandez.core.StorageRoots
import com.winhub.hernandez.ui.theme.AppThemeState
import com.winhub.hernandez.ui.theme.LocalAccentDim
import com.winhub.hernandez.ui.components.ContainerGlossarySheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun iconFor(screen: Screen): Int = when (screen) {
    Screen.Containers    -> R.drawable.icon_menu_container
    Screen.Games         -> R.drawable.icon_games
    Screen.Contents      -> R.drawable.icon_menu_contents
    Screen.InputControls -> R.drawable.icon_gamepad
    Screen.AdrenoTools   -> R.drawable.icon_menu_gpu
    Screen.Saves         -> R.drawable.icon_save
    Screen.SaveManager   -> R.drawable.icon_save
    Screen.FileManager   -> R.drawable.icon_menu_file_manager
    Screen.Settings      -> R.drawable.icon_settings
    Screen.Appearance    -> R.drawable.icon_palette
    Screen.SocialHub     -> R.drawable.icon_group
    else                 -> R.drawable.icon_container
}

// Show the My Account / Sign in row at the top of the nav drawer. Off by default: the account
// entry is hidden from the menu, while sign-in stays reachable from the Games header avatar.
private const val SIGN_IN_IN_DRAWER = false

@Composable
fun AppDrawerContent(
    currentRoute: String,
    onNavigate: (Screen) -> Unit,
    onLaunchStore: (Screen) -> Unit,
    onAbout: () -> Unit,
    // PHASE 3 (optional accounts): null = logged out (subtle "Sign in" row); non-null = profile header.
    account: AccountManager.Account? = null,
    onMyAccount: () -> Unit = {},
) {
    var showHelp by remember { mutableStateOf(false) }
    // Community → Help: the container/shortcut editor's newcomer glossary, moved here for the room
    // it needs (the editors' "What is all this?" entries were removed).
    var showGlossary by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showHelp) {
        HelpSupportDialog(
            onDismiss = { showHelp = false },
            onOpenUrl = { url ->
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        )
    }
    if (showGlossary) {
        ContainerGlossarySheet(initialQuery = "", onDismiss = { showGlossary = false })
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(MaterialTheme.colorScheme.background)
            .border(0.5.dp, Color(0xFF2E2E2E))
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(18.dp))

        // My Account / Sign in header — hidden from the drawer (SIGN_IN_IN_DRAWER = false), so the
        // library section opens the menu. Sign-in itself is still reachable from the Games header
        // avatar and the Shortcuts screen icon, which drive AccountUiBus directly.
        if (SIGN_IN_IN_DRAWER) {
            DrawerAccountHeader(account = account, onMyAccount = onMyAccount)
        }

        DrawerSectionHeader("Library")
        DrawerItem(Screen.Games,         currentRoute, onNavigate)
        DrawerItem(Screen.Containers,    currentRoute, onNavigate)
        DrawerItem(Screen.FileManager,   currentRoute, onNavigate)
        DrawerItem(Screen.SaveManager,   currentRoute, onNavigate)

        DrawerSectionHeader("System", showDivider = true)
        DrawerItem(Screen.Settings,      currentRoute, onNavigate)
        DrawerItem(Screen.InputControls, currentRoute, onNavigate)
        DrawerItem(Screen.Contents,      currentRoute, onNavigate)

        // Community feed over GitHub Issues (winhub-emu/social-hub). Own sign-in screen until a
        // GitHub device-flow session exists — see ui/XServerSocialTab.kt.
        DrawerSectionHeader("Community", showDivider = true)
        DrawerItem(Screen.SocialHub,     currentRoute, onNavigate)
        DrawerIconItem(
            label = "Help",
            icon = Icons.Filled.MenuBook,
            onClick = { showGlossary = true },
        )

        // Hideable from Settings → Side Menu — the whole section, header included, so turning
        // it off leaves no orphaned divider behind.
        val showStores by AppThemeState.showStores.collectAsState()
        if (showStores) {
            DrawerSectionHeader("Stores", note = "· unchanged", showDivider = true)
            Screen.storeItems.forEach { screen ->
                DrawerStoreItem(screen, onLaunchStore)
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 6.dp)
        )

        DrawerIconItem(
            label = "About",
            icon = Icons.Filled.Info,
            onClick = onAbout,
        )
        DrawerIconItem(
            label = "Help and Support",
            icon = Icons.Filled.HelpOutline,
            onClick = { showHelp = true },
        )

        StorageWidget()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun DrawerAccountHeader(account: AccountManager.Account?, onMyAccount: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onMyAccount)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        AccountAvatar(
            avatarUrl = account?.displayAvatarUrl,
            size = 40.dp,
            fallbackTint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (account != null) {
                Text(
                    text = account.username,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = "My account",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            } else {
                Text(
                    text = "Sign in / My account",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Optional — to manage your shared configs",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
        // Steam connection status next to the account name (self-gates to signed-in users; tap when
        // offline to retry). Its own click consumes the tap, so it won't trigger the header's onMyAccount.
        Spacer(Modifier.width(8.dp))
        com.winhub.hernandez.store.SteamConnectionPill()
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 20.dp, top = 6.dp, end = 20.dp, bottom = 2.dp)
    )
}

@Composable
private fun DrawerSectionHeader(title: String, note: String? = null, showDivider: Boolean = false) {
    if (showDivider) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 2.dp)
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 22.dp, top = 14.dp, end = 22.dp, bottom = 6.dp),
    ) {
        Text(
            text = title.uppercase(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
        )
        if (note != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = note,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun StorageWidget() {
    val context = LocalContext.current
    var usedBytes by remember { mutableStateOf(0L) }
    var totalBytes by remember { mutableStateOf(0L) }
    // Removable volumes found at open, as (label, used, total). Empty on a device with no card,
    // which is the normal case — the row simply does not appear.
    var removable by remember { mutableStateOf<List<Triple<String, Long, Long>>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            val path = Environment.getExternalStorageDirectory().path
            val stat = StatFs(path)
            val total = stat.totalBytes
            val available = stat.availableBytes
            usedBytes = total - available
            totalBytes = total
        } catch (_: Exception) {}

        // Ask StorageRoots rather than listing /storage here. A card can be mounted and healthy
        // yet missing from this process's mount view, which is exactly what made earlier SD
        // detection unreliable; StorageRoots already reconciles StorageManager, the app-specific
        // dirs and the filesystem listing.
        // Off the main thread: this crosses Binder to StorageManager and lists directories, and
        // it runs while the drawer is opening.
        removable = withContext(Dispatchers.IO) {
            try {
                StorageRoots.list(context)
                    .filter { it.removable }
                    .mapNotNull { root ->
                        try {
                            val stat = StatFs(root.dir.path)
                            val total = stat.totalBytes
                            // A volume we cannot stat reports zero. A card reading "0 B / 0 B" is
                            // worse than no card at all, so drop it instead.
                            if (total <= 0L) null
                            else Triple(root.label, total - stat.availableBytes, total)
                        } catch (_: Exception) {
                            null
                        }
                    }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    val showInternal by AppThemeState.showInternalStorage.collectAsState()
    val showSd by AppThemeState.showSdStorage.collectAsState()
    val sdCards = if (showSd) removable else emptyList()
    val anyShown = (showInternal && totalBytes > 0) || sdCards.isNotEmpty()

    // Leading spacer lives here rather than at the call site, so turning both cards off leaves no
    // dead gap at the bottom of the drawer.
    if (anyShown) Spacer(Modifier.height(8.dp))
    if (showInternal) {
        StorageCard("Internal Storage", usedBytes, totalBytes)
    }
    sdCards.forEachIndexed { index, (label, used, total) ->
        if (showInternal || index > 0) Spacer(Modifier.height(4.dp))
        StorageCard(label, used, total)
    }
}

@Composable
private fun StorageCard(label: String, usedBytes: Long, totalBytes: Long) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Color(0xFF242424), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (totalBytes > 0) {
                    Text(
                        text = "${formatBytes(usedBytes)} / ${formatBytes(totalBytes)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color(0xFF333333),
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000_000L -> "%.1f TB".format(bytes / 1_000_000_000_000.0)
        bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000L -> "%.1f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}

@Composable
private fun DrawerItem(
    screen: Screen,
    currentRoute: String,
    onNavigate: (Screen) -> Unit,
    showNew: Boolean = false,
) {
    val accent = MaterialTheme.colorScheme.primary
    val accentDim = LocalAccentDim.current
    val selected = currentRoute == screen.route
    val bgBrush = if (selected)
        Brush.verticalGradient(listOf(accentDim, accent.copy(alpha = 0.10f)))
    else
        Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
    val borderColor = if (selected) accent.copy(alpha = 0.6f) else Color.Transparent
    val contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgBrush, RoundedCornerShape(12.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onNavigate(screen) },
    ) {
        if (selected) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.16f), Color.Transparent),
                        radius = size.minDimension / 2f
                    ),
                    radius = size.minDimension / 2f
                )
                // Left accent glow bar.
                val barW = 3.dp.toPx()
                val barH = size.height * 0.64f
                drawRoundRect(
                    color = accent,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, (size.height - barH) / 2f),
                    size = androidx.compose.ui.geometry.Size(barW, barH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2f, barW / 2f),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
        ) {
            Icon(
                painter = painterResource(iconFor(screen)),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(25.dp),
            )
            Spacer(Modifier.width(13.dp))
            Text(
                text = screen.label,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
            )
            if (showNew) {
                Spacer(Modifier.width(8.dp))
                NewBadge()
            }
        }
    }
}

@Composable
private fun NewBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            text = "NEW",
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp,
        )
    }
}

@Composable
private fun DrawerStoreItem(screen: Screen, onLaunchStore: (Screen) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onLaunchStore(screen) }
            .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Storefront,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = screen.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DrawerIconItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // Matches DrawerItem's metrics (12dp box padding + 13dp row padding, 25dp icon,
            // 13dp gap) so Help/About line up with the Library/System entries instead of
            // sitting a few dp to the left of them.
            .padding(horizontal = 25.dp, vertical = 11.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(25.dp),
        )
        Spacer(Modifier.width(13.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun HelpSupportDialog(onDismiss: () -> Unit, onOpenUrl: (String) -> Unit) {
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Help & Support") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "For bug reports, feature requests, and support, visit the GitHub repository.",
                    color = MaterialTheme.colorScheme.onSurface
                )
                SupportLink(
                    label = "GitHub Repository",
                    url = "https://github.com/The412Banner/Bannerlator",
                    onOpenUrl = onOpenUrl
                )
                SupportLink(
                    label = "Report an Issue",
                    url = "https://github.com/The412Banner/Bannerlator/issues",
                    onOpenUrl = onOpenUrl
                )
                SupportLink(
                    label = "Discord Community",
                    url = "https://discord.gg/kk6GR3C2pX",
                    onOpenUrl = onOpenUrl
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun SupportLink(label: String, url: String, onOpenUrl: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenUrl(url) }
            .padding(vertical = 4.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
