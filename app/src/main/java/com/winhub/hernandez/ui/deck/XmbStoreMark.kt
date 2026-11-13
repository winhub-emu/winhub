package com.winhub.hernandez.ui.deck

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.R
import com.winhub.hernandez.container.Shortcut
import java.util.Collections
import java.util.WeakHashMap

/** Where a game comes from, as the small mark on its cover (the XMB games row and the All Games page). */
internal enum class StoreMark { STEAM, EPIC, GOG, AMAZON, XBOX, ANDROID, WINDOWS }

// Kept out of the enum so classifying a shortcut never touches the resources (and a unit test can load the enum).
@DrawableRes
private fun StoreMark.logoRes(): Int? = when (this) {
    StoreMark.STEAM -> R.drawable.store_logo_steam
    StoreMark.EPIC -> R.drawable.store_logo_epic
    StoreMark.GOG -> R.drawable.store_logo_gog
    StoreMark.AMAZON -> R.drawable.store_logo_amazon
    StoreMark.XBOX -> R.drawable.store_logo_xbox
    StoreMark.ANDROID, StoreMark.WINDOWS -> null
}

/**
 * The mark for a [storeLabelOf] label. The Linux entry is Valve's own Steam client, so it wears Steam's logo; a label that is
 * no known store is a plain exe the user added, which runs as a Windows program.
 */
internal fun storeMarkOfLabel(label: String): StoreMark = when (label) {
    "Steam", "Linux", "Windows Steam" -> StoreMark.STEAM
    "Epic Games" -> StoreMark.EPIC
    "GOG" -> StoreMark.GOG
    "Amazon Games" -> StoreMark.AMAZON
    "Xbox" -> StoreMark.XBOX
    "Android" -> StoreMark.ANDROID
    else -> StoreMark.WINDOWS
}

// Keyed by the Shortcut object: the library rebuilds them on every refresh, which drops the old entries (and the map lets
// go of them), so an edited shortcut is classified again and a scrolling list never classifies the same one twice.
private val marks: MutableMap<Shortcut, StoreMark> = Collections.synchronizedMap(WeakHashMap<Shortcut, StoreMark>())

/**
 * True when [path] (a Windows or Android path) is a game folder the Epic store installed into, i.e. an `epic_games`
 * segment followed by another one. A game added with "+" or the folder scan carries no Epic tag, only this path.
 */
internal fun isInEpicFolder(path: String?): Boolean {
    val parts = (path ?: return false).replace('\\', '/').split('/')
    val at = parts.indexOfFirst { it.equals("epic_games", ignoreCase = true) }
    return at >= 0 && at + 1 < parts.size && parts[at + 1].isNotEmpty()
}

/**
 * The one place that decides a shortcut's mark: the same rules as its store shelf and its filter chip, plus the Epic
 * folder for shortcuts that carry no store tag (they would otherwise get the plain Windows mark).
 */
internal fun storeMarkOf(s: Shortcut): StoreMark = marks.getOrPut(s) {
    val mark = storeMarkOfLabel(storeLabelOf(s))
    if (mark == StoreMark.WINDOWS && isInEpicFolder(s.path)) StoreMark.EPIC else mark
}

/** Decodes the store logos ahead of time so a list full of marks finds them ready. */
internal suspend fun warmStoreMarks(context: Context) {
    warmXmbLogos(context, StoreMark.entries.mapNotNull { it.logoRes() })
}

/**
 * A store's mark, drawn straight on the cover with nothing behind it. Put it at the cover's bottom-end corner; it is sized
 * in dp, so inside a scaled cover it scales with it.
 */
@Composable
internal fun XmbStoreMark(mark: StoreMark, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Box(
        modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        val logo = mark.logoRes()
        when {
            logo != null -> rememberXmbLogo(logo)?.let { bmp ->
                Image(
                    bmp, contentDescription = null, contentScale = ContentScale.Fit,
                    // Xbox's mark is drawn white on its own green; here it sits on black.
                    colorFilter = if (mark == StoreMark.XBOX) ColorFilter.tint(Color.White) else null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            mark == StoreMark.ANDROID -> Icon(Icons.Filled.Android, contentDescription = null, tint = Color(0xFF3DDC84), modifier = Modifier.fillMaxSize())
            else -> WindowsGlyph(Modifier.fillMaxSize())
        }
    }
}

// The four-pane Windows flag, drawn: no drawable to ship for it.
@Composable
private fun WindowsGlyph(modifier: Modifier) {
    Canvas(modifier) {
        val gap = size.minDimension * 0.08f
        val pane = (size.minDimension - gap) / 2f
        for (row in 0..1) for (col in 0..1) {
            drawRect(Color(0xFF4FC3F7), topLeft = Offset(col * (pane + gap), row * (pane + gap)), size = Size(pane, pane))
        }
    }
}
