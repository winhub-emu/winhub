package com.winhub.hernandez.store.download

import android.content.Intent
import com.winhub.hernandez.store.DownloadManagerActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Reusable ⬇ toolbar action for a store header. Shows a live count badge of active
 * downloads (QUEUED/DOWNLOADING/PAUSED) collected from [DownloadRegistry.activeCount];
 * the badge hides entirely at 0. Tapping opens the cross-store [DownloadManagerActivity].
 *
 * Self-contained (grabs its own [LocalContext] + launches the Activity) so a header just
 * drops `DownloadsButton()` into its Row — matching the other single-line IconButtons in
 * the Steam headers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsButton(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val activeCount by DownloadRegistry.activeCount.collectAsStateWithLifecycle()

    IconButton(
        onClick = {
            ctx.startActivity(Intent(ctx, DownloadManagerActivity::class.java))
        },
        modifier = modifier,
    ) {
        BadgedBox(
            badge = {
                if (activeCount > 0) {
                    Badge { Text(activeCount.toString()) }
                }
            },
        ) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = "Downloads & Library",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
