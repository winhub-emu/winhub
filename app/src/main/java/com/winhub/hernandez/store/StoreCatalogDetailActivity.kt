package com.winhub.hernandez.store

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.core.AppOrientation
import com.winhub.hernandez.store.download.InfoChip
import com.winhub.hernandez.store.download.MediaImage
import com.winhub.hernandez.store.download.MediaTab
import com.winhub.hernandez.store.download.Store
import com.winhub.hernandez.store.download.StoreMedia
import com.winhub.hernandez.store.download.StoreBadge
import com.winhub.hernandez.store.download.StoreDetailScaffold
import com.winhub.hernandez.store.download.StoreGearItem
import com.winhub.hernandez.store.download.StorePrimaryAction
import com.winhub.hernandez.store.download.StoreSection
import com.winhub.hernandez.store.download.StoreStatusText
import com.winhub.hernandez.ui.theme.WinlatorTheme
import org.json.JSONObject

/**
 * Detail page for a catalog title the user does NOT own — the Steam detail layout
 * ([StoreDetailScaffold]) with the store's description and media, a price row, and one
 * primary action that opens the store page in the in-app WebView ("Get for free" / "View on …").
 *
 * Serves both GOG and Epic: the [CatalogItem] rides in the intent, the description + media
 * are fetched on open from the matching public endpoint ([GogStoreCatalog.product] /
 * [EpicStoreCatalog.offer]). Owned titles never land here — the storefronts route them to the
 * store's own detail page, which carries install / DLC / cloud saves.
 */
class StoreCatalogDetailActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_ITEM = "catalog_item_json"

        fun intent(ctx: Context, item: CatalogItem): Intent =
            Intent(ctx, StoreCatalogDetailActivity::class.java).putExtra(EXTRA_ITEM, toJson(item).toString())

        private fun toJson(i: CatalogItem): JSONObject = JSONObject().apply {
            put("store", i.store.name); put("id", i.id); put("title", i.title)
            put("image", i.imageUrl ?: ""); put("tall", i.tallImageUrl ?: "")
            put("tags", i.tags); put("free", i.isFree); put("hasPrice", i.hasPrice)
            put("final", i.finalPrice); put("orig", i.originalPrice); put("disc", i.discountPercent)
            put("url", i.storeUrl); put("dev", i.developer); put("rel", i.releaseDate); put("desc", i.description)
            put("extra", JSONObject(i.extra as Map<*, *>))
        }

        private fun fromJson(s: String): CatalogItem? = runCatching {
            val o = JSONObject(s)
            val extra = HashMap<String, String>()
            o.optJSONObject("extra")?.let { e -> e.keys().forEach { k -> extra[k] = e.optString(k) } }
            CatalogItem(
                store = Store.valueOf(o.optString("store", "GOG")),
                id = o.optString("id"), title = o.optString("title"),
                imageUrl = o.optString("image").ifBlank { null }, tallImageUrl = o.optString("tall").ifBlank { null },
                tags = o.optString("tags"), isFree = o.optBoolean("free"), hasPrice = o.optBoolean("hasPrice"),
                finalPrice = o.optString("final"), originalPrice = o.optString("orig"), discountPercent = o.optInt("disc"),
                storeUrl = o.optString("url"), developer = o.optString("dev"), releaseDate = o.optString("rel"),
                description = o.optString("desc"), extra = extra,
            )
        }.getOrNull()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppOrientation.apply(this)
        val item = intent.getStringExtra(EXTRA_ITEM)?.let { fromJson(it) }
        if (item == null || item.id.isBlank()) { finish(); return }
        setContent {
            StoreXmbTheme {
                DeckStoreTheme {
                CatalogDetailScreen(
                    item = item,
                    onBack = { finish() },
                    onOpenWeb = { url, title -> startActivity(StoreWebActivity.intent(this, url, title)) },
                    onOpenBrowser = { url -> runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
                )
            
                }}
        }
    }
}

/** What the page fetched on open, normalised across the two stores. */
private class CatalogDetailData(
    val lead: String,
    val full: String,
    val media: StoreMedia,
    val releaseDate: String,
    val hero: String?,
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun CatalogDetailScreen(
    item: CatalogItem,
    onBack: () -> Unit,
    onOpenWeb: (url: String, title: String) -> Unit,
    onOpenBrowser: (url: String) -> Unit,
) {
    val context = LocalContext.current
    val storeLabel = when (item.store) { Store.GOG -> "GOG.com"; Store.EPIC -> "Epic Games Store"; else -> item.store.name }
    var tab by remember { mutableStateOf(0) }
    var data by remember { mutableStateOf<CatalogDetailData?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(item.id) {
        loading = true
        data = runCatching {
            when (item.store) {
                Store.GOG -> GogStoreCatalog.product(item.id)?.let {
                    CatalogDetailData(it.lead, it.full, it.media, it.releaseDate, it.background)
                }
                Store.EPIC -> EpicStoreCatalog.offer(item.extra["namespace"].orEmpty(), item.id)?.let {
                    val shots = it.screenshots.map { url -> MediaImage(url, url) }
                    CatalogDetailData(it.description, it.longDescription, StoreMedia(shots, emptyList()), it.releaseDate, it.wideImage)
                }
                else -> null
            }
        }.getOrNull()
        loading = false
    }

    val tabs = listOf("Details", "Media")
    val primaryLabel = when {
        item.isFree -> "Get for free on $storeLabel"
        else -> "View on $storeLabel"
    }

    StoreDetailScaffold(
        onBack = onBack,
        title = item.title,
        storeBadge = { StoreBadge(item.store) },
        hero = {
            CatalogArt(
                candidates = listOfNotNull(data?.hero) + item.wideArt(),
                title = item.title,
                modifier = Modifier.fillMaxSize(),
                aspectRatio = null,
            )
        },
        subtitle = {
            Spacer(Modifier.height(6.dp))
            CatalogPriceRow(item)
            if (item.tags.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.tags,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        },
        primary = StorePrimaryAction.Button(
            label = primaryLabel,
            enabled = item.storeUrl.isNotBlank(),
            onClick = { onOpenWeb(item.storeUrl, item.title) },
        ),
        gear = listOf(
            StoreGearItem("🌐", "Open in browser", enabled = item.storeUrl.isNotBlank()) { onOpenBrowser(item.storeUrl) },
        ),
        infoLine = if (item.isFree) "Free claims go through the store's own checkout — you'll be signed in there."
        else "Purchases go through $storeLabel. Owned titles appear in your Library after a sync.",
        tabs = tabs,
        selectedTab = tab,
        onSelectTab = { tab = it },
    ) {
        when (tab) {
            0 -> Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.developer.isNotBlank()) InfoChip(item.developer)
                    val rel = data?.releaseDate?.takeIf { it.isNotBlank() } ?: item.releaseDate
                    if (rel.isNotBlank()) InfoChip(rel.take(10).replace('.', '-'))
                    item.extra["rating"]?.let { InfoChip("★ $it/50") }
                    item.extra["type"]?.let { InfoChip(it.replaceFirstChar { c -> c.uppercase() }) }
                    if (item.isFree) InfoChip("Free")
                }
                Spacer(Modifier.height(12.dp))
                val d = data
                when {
                    loading && d == null -> Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    else -> {
                        val lead = (d?.lead ?: item.description).let { plain(it) }
                        val full = d?.full?.let { plain(it) }.orEmpty()
                        if (lead.isNotBlank()) {
                            Text(
                                text = lead,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (full.isNotBlank() && full != lead) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = full,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (lead.isBlank() && full.isBlank()) {
                            StoreStatusText("No description available for this title.")
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                StoreSection(title = "About this page") {
                    StoreStatusText(
                        "This title isn't in your $storeLabel library yet. Get it on the store page and it " +
                            "will show up under Library with install, DLC and cloud-save controls.",
                    )
                }
            }

            else -> MediaTab(
                media = data?.media,
                loading = loading && data == null,
                storeLabel = storeLabel,
                onOpenVideo = { MediaPlayback.openVideo(context, it) },
            )
        }
    }
}

private fun plain(html: String): String =
    if (html.isBlank()) "" else runCatching {
        Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().trim()
    }.getOrDefault(html)
