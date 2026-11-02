package com.winhub.hernandez.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.container.Container
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.core.FileUtils
import com.winhub.hernandez.store.StarLaunchBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.IOException

/** The per-game tools Home's classic menu offers on top of the basics (the same flows the Games screen's ⋮ menu runs). */
internal enum class HubTool { DETAILS, COVERS, CLONE, DRIVE_C, CHANGE_EXE }

internal class HubToolRequest(val tool: HubTool, val shortcut: Shortcut)

/**
 * Hosts the dialog for the open [request]: Game details, Search covers, Clone to container, Copy to
 * Drive C and Change executable. Each reuses the Games screen's own flow; [onChanged] fires after
 * anything that may have changed the game so Home can re-read its library.
 */
@Composable
internal fun HubGameToolsHost(
    request: HubToolRequest?,
    containers: List<Container>,
    onClose: () -> Unit,
    onChanged: (Shortcut) -> Unit,
) {
    if (request == null) return
    val s = request.shortcut
    when (request.tool) {
        HubTool.DETAILS -> GameDetailsSheet(shortcut = s, onDismiss = onClose, onSaved = { onChanged(s) })
        HubTool.DRIVE_C -> CopyToDriveCCoordinator(target = s, onFinished = { onClose(); onChanged(s) })
        HubTool.CHANGE_EXE -> ChangeExecutableCoordinator(target = s, onFinished = { onClose(); onChanged(s) })
        HubTool.CLONE -> HubCloneDialog(s, containers, onClose)
        HubTool.COVERS -> HubCoverSearchDialog(s, onClose) { onChanged(s) }
    }
}

@Composable
private fun HubCloneDialog(s: Shortcut, containers: List<Container>, onClose: () -> Unit) {
    val context = LocalContext.current
    OutlinedAlertDialog(
        onDismissRequest = onClose,
        title = { Text("Clone to container") },
        text = {
            if (containers.isEmpty()) {
                Text("No containers yet.")
            } else {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    containers.forEach { c ->
                        Text(
                            text = c.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable {
                                    val ok = s.cloneToContainer(c)
                                    onClose()
                                    Toast.makeText(context, if (ok) "Shortcut cloned." else "Failed to clone shortcut.", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

@Composable
private fun HubCoverSearchDialog(s: Shortcut, onClose: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember(s.file.path) { mutableStateOf(true) }
    val covers = remember(s.file.path) { mutableStateListOf<Pair<Bitmap, String>>() }

    LaunchedEffect(s.file.path) {
        val found = withContext(Dispatchers.IO) {
            val out = mutableListOf<Pair<Bitmap, String>>()
            try {
                val arr = JSONArray(StarLaunchBridge.sgdbFetchGridsJson(s.name))
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val thumb = o.optString("thumb", "")
                    val full = o.optString("url", "")
                    if (thumb.isEmpty() || full.isEmpty()) continue
                    val bmp = fetchBitmap(thumb, 10_000, 10_000) ?: continue
                    out.add(bmp to full)
                }
            } catch (e: org.json.JSONException) {
                android.util.Log.w("HubCovers", "bad SteamGridDB response", e)
            }
            out
        }
        covers.clear()
        covers.addAll(found)
        loading = false
    }

    OutlinedAlertDialog(
        onDismissRequest = onClose,
        title = { Text("Search covers for \"${s.name}\"") },
        text = {
            when {
                loading -> Text("Searching SteamGridDB...")
                covers.isEmpty() -> Text("No covers found.")
                else -> Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    covers.forEach { (bmp, url) ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    scope.launch {
                                        val saved = withContext(Dispatchers.IO) { saveCover(s, url) }
                                        onClose()
                                        if (saved) onSaved()
                                        Toast.makeText(context, if (saved) "Cover saved." else "Couldn't download that cover.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

/** Downloads [url] as a bitmap; null on any network/decode failure. Blocking. */
private fun fetchBitmap(url: String, connectMs: Int, readMs: Int): Bitmap? = try {
    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
    conn.connectTimeout = connectMs
    conn.readTimeout = readMs
    try { conn.inputStream.use { BitmapFactory.decodeStream(it) } } finally { conn.disconnect() }
} catch (e: IOException) {
    android.util.Log.w("HubCovers", "fetch $url", e)
    null
}

/** Saves the full-size cover as the shortcut's custom art + its container icon, same as the Games screen. Blocking. */
private fun saveCover(s: Shortcut, url: String): Boolean {
    val full = fetchBitmap(url, 15_000, 20_000) ?: return false
    s.saveCustomCoverArt(full)
    s.icon = full
    val iconsDir = s.container.getIconsDir(64)
    if (iconsDir != null) {
        if (!iconsDir.exists()) iconsDir.mkdirs()
        val iconName = runCatching { s.file.readLines().firstOrNull { it.startsWith("Icon=") }?.substringAfter("Icon=")?.trim() }.getOrNull() ?: s.name
        FileUtils.saveBitmapToFile(full, File(iconsDir, "$iconName.png"))
    }
    return true
}
