package com.winhub.hernandez.ui.simple

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.winhub.hernandez.container.Shortcut
import java.io.File

/**
 * A game's landscape art for the Steam-style Home: what the game picked, then for a Steam game what Steam publishes for the app
 * (its hero, header and capsule on the public CDN), then its own cover. Nothing is downloaded here: the pictures load where they show.
 */
internal object LabArt {
    class Pic(val model: Any, val blur: Boolean = false)

    /** Changes when the art sources change; readers recompose with it. */
    var tick by mutableIntStateOf(0)
        private set

    fun appId(s: Shortcut): Int? = s.getExtra("steamAppId", "").toIntOrNull()?.takeIf { it > 0 }

    /** Kept for the callers that ask for the art to be found; the sources are known up front, so there is nothing to wait for. */
    fun resolve(context: Context, s: Shortcut) {}

    suspend fun resolveAndWait(context: Context, s: Shortcut) {}

    private fun cdn(appId: Int, file: String) = "https://cdn.akamai.steamstatic.com/steam/apps/$appId/$file"

    fun models(s: Shortcut, slot: String): List<Any> {
        val id = appId(s)
        val custom = s.customCoverArtPath?.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.isFile }
        val steam: List<Any> = if (id == null) emptyList() else when (slot) {
            "bg" -> listOf(cdn(id, "library_hero.jpg"), cdn(id, "header.jpg"))
            "wide" -> listOf(cdn(id, "header.jpg"), cdn(id, "capsule_616x353.jpg"))
            else -> emptyList()
        }
        return (steam + listOfNotNull(custom, s.icon)).distinct()
    }
}

/** A picture from [pics], the next one when one does not load; nothing when none does. [onLoaded] runs when one has loaded. */
@Composable
internal fun LabPicture(
    pics: List<LabArt.Pic>,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    onNone: (@Composable () -> Unit)? = null,
    onLoaded: (() -> Unit)? = null,
) {
    var i by remember(pics.map { it.model }) { mutableIntStateOf(0) }
    val p = pics.getOrNull(i)
    if (p == null) {
        onNone?.invoke()
        return
    }
    AsyncImage(
        model = p.model,
        contentDescription = null,
        contentScale = contentScale,
        alignment = alignment,
        modifier = modifier.then(if (p.blur) Modifier.blur(22.dp) else Modifier),
        onState = { if (it is AsyncImagePainter.State.Error) i++ else if (it is AsyncImagePainter.State.Success) onLoaded?.invoke() },
    )
}
