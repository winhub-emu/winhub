package com.winhub.hernandez.store.xbox

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/** One game from the Microsoft Store catalog, with how this account can play it: installed on the device ([pc]), streamed ([cloud]) or both. */
data class XboxGame(
    val id: String,
    val title: String,
    val heroUrl: String? = null,
    val posterUrl: String? = null,
    val publisher: String = "",
    val developer: String = "",
    val description: String = "",
    val sizeBytes: Long = 0L,
    val market: String = "US",
    /** "owned", "gamepass" or "" (neither). */
    val access: String = "",
    /** Has a PC (MSIXVC) package that can be installed. */
    val pc: Boolean = true,
    /** Can be streamed with Xbox Cloud Gaming on this account. */
    val cloud: Boolean = false,
    /** The product that streams when it is not this one (the console edition of a PC game). */
    val cloudId: String? = null,
) {
    val owned get() = access == "owned"
    val gamePass get() = access == "gamepass"
    val storeUrl get() = "https://www.xbox.com/games/store/$id"
    /** Starts the game in Microsoft's cloud player; the page also signs in when needed. */
    val cloudUrl get() = "https://www.xbox.com/play/launch/${title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "game" }}/${cloudId ?: id}"
}

/**
 * One card per game: Microsoft lists a game's console and PC editions as separate products, so a cloud-only
 * product whose title matches a PC game is folded into it (the PC card then also streams).
 */
internal fun mergeEditions(games: List<XboxGame>): List<XboxGame> {
    fun key(title: String) = title.lowercase().replace(Regex("[^a-z0-9]+"), "")
    val cloudOnly = games.filter { !it.pc && it.cloud }.groupBy { key(it.title) }
    val folded = HashSet<String>()
    val merged = games.mapNotNull { game ->
        when {
            !game.pc -> game
            game.cloud -> game
            else -> cloudOnly[key(game.title)]?.firstOrNull()?.let { console ->
                folded.add(console.id)
                game.copy(cloud = true, cloudId = console.id)
            } ?: game
        }
    }
    return merged.filter { it.id !in folded }
}

/**
 * Microsoft's public Xbox Cloud Gaming lists (catalog.gamepass.com "sigls", ids as used by redphx's Better xCloud,
 * MIT): the Game Pass cloud games, and every cloud game including those streamed from an owned copy.
 */
internal data class XboxCloudCatalog(val gamePass: Set<String>, val all: Set<String>) {
    fun streams(id: String, access: String): Boolean = when (access) {
        "gamepass" -> id in gamePass
        "owned" -> id in all || id in gamePass
        else -> false
    }
}

/**
 * The Xbox library and catalog (ported from DroidDeck's XboxCatalog, GPL-3.0): which products the account
 * owns or has through PC Game Pass (from the backend's entitlement list), and the PC (MSIXVC) games'
 * metadata from Microsoft's public display catalog. Console-only and UWP/APPX packages are left out.
 */
internal object XboxCatalog {
    private val productId = Regex("[A-Z0-9]{12}")
    /** PC Game Pass / Game Pass Ultimate plan product ids (DroidDeck's list). */
    private val plans = setOf("CFQ7TTC0KGQ8", "CFQ7TTC0KHS0", "CFQ7TTC0P85B", "CFQ7TTC0K5DJ", "CFQ7TTC0K6L8", "CFQ7TTC10QFD")

    fun access(json: JSONObject, account: String, market: String, now: Instant = Instant.now(), report: (String) -> Unit = {}): Map<String, String> {
        require(json.getInt("schema") == 1 && json.getString("account") == account && json.getString("market") == market)
        val age = java.time.Duration.between(timestamp(json.getString("checkedAt")), now).seconds
        require(age in -60..299)
        val memberships: Set<String>? = if (json.has("subscriptions")) {
            val subscriptions = json.getJSONArray("subscriptions")
            require(subscriptions.length() <= plans.size)
            val seen = HashSet<String>()
            buildSet {
                for (i in 0 until subscriptions.length()) {
                    val row = subscriptions.getJSONObject(i)
                    val id = row.getString("productId")
                    require(id in plans && seen.add(id))
                    val status = row.getString("status")
                    val expiry = timestamp(row.getString("endDate"))
                    if (status == "Active" && expiry.isAfter(now)) add(id)
                }
            }
        } else null
        val array = json.getJSONArray("entitlements")
        require(array.length() <= 10_000)
        val rows = LinkedHashMap<String, JSONObject>()
        val blocked = HashSet<String>()
        for (i in 0 until array.length()) {
            val row = array.optJSONObject(i) ?: continue
            val id = row.optString("productId")
            if (!productId.matches(id)) continue
            val valid = row.opt("status") is String && row.opt("isTrial") is Boolean && row.opt("special") is Boolean &&
                row.opt("sharingSource") is String && runCatching { timestamp(row.getString("endDate")) }.isSuccess &&
                (row.isNull("satisfiedByProductId") || productId.matches(row.optString("satisfiedByProductId")))
            if (!valid || id in rows || id in blocked) { blocked.add(id); rows.remove(id) } else rows[id] = row
        }
        fun active(row: JSONObject) = row.getString("status") == "Active" && !row.getBoolean("isTrial") && !row.getBoolean("special") &&
            row.getString("sharingSource") == "None" && timestamp(row.getString("endDate")).isAfter(now)
        fun activePlan(id: String) = rows[id]?.let(::active) ?: (id in memberships.orEmpty())
        fun kind(id: String, visited: Set<String> = emptySet()): String? {
            if (id in blocked || id in visited || visited.size >= 16) return null
            val row = rows[id] ?: return null
            if (!active(row)) return null
            val parent = row.optString("satisfiedByProductId").takeUnless { it.isBlank() || it == "null" }
            if (parent == null) return if (id in plans) null else "owned"
            if (parent in blocked) return null
            if (parent in plans) return if (activePlan(parent)) "gamepass" else null
            return kind(parent, visited + id)
        }
        val result = rows.keys.mapNotNull { id -> kind(id)?.let { id to it } }.toMap()
        val missingPlan = rows.values.count { row -> row.optString("satisfiedByProductId").let { it in plans && !activePlan(it) } }
        report("library entitlements=${array.length()} invalid=${array.length() - rows.size} restricted=${rows.values.count { !active(it) }} " +
            "unverified-plan=$missingPlan owned=${result.values.count { it == "owned" }} gamepass=${result.values.count { it == "gamepass" }}")
        return result
    }

    private fun timestamp(value: String): Instant = java.time.OffsetDateTime.parse(value).toInstant()

    private const val CLOUD_GAME_PASS = "29a81209-df6f-41fd-a528-2ae6b91f719c"
    private const val CLOUD_ALL = "ce573635-7c18-4d0c-9d68-90b932393470"

    fun cloudCatalog(market: String = Xodus.market()): XboxCloudCatalog =
        XboxCloudCatalog(cloudList(CLOUD_GAME_PASS, market), cloudList(CLOUD_ALL, market))

    private fun cloudList(id: String, market: String): Set<String> {
        val body = getArray("https://catalog.gamepass.com/sigls/v2?id=$id&language=en-us&market=$market")
        return objects(body).mapNotNull { it.optString("id").uppercase().takeIf { id -> productId.matches(id) } }.toSet()
    }

    fun products(ids: List<String>, access: Map<String, String> = emptyMap(), market: String = Xodus.market()): List<XboxGame> =
        rawProducts(ids, market).mapNotNull { product(it, access, market) }

    fun rawProducts(ids: List<String>, market: String = Xodus.market()): List<JSONObject> =
        ids.map { it.uppercase() }.filter { productId.matches(it) }.distinct().chunked(25).flatMap { batch ->
            val body = get("https://displaycatalog.mp.microsoft.com/v7.0/products?bigIds=${batch.joinToString(",")}&market=$market&languages=en-us,neutral")
            objects(body.optJSONArray("Products")).filter { it.optString("ProductId").uppercase() in batch }
        }

    fun search(query: String, access: Map<String, String>): List<XboxGame> {
        if (productId.matches(query.uppercase())) return products(listOf(query.uppercase()), access)
        if (query.length !in 2..100) return emptyList()
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val body = get("https://apps.microsoft.com/api/products/search?query=$q&mediaType=games&age=all&price=all&category=all&subscription=all&gl=${Xodus.market()}&hl=en-us")
        val ids = objects(body.optJSONArray("productsList")).map { it.optString("productId") }.take(60)
        return products(ids, access)
    }

    fun featured(access: Map<String, String>): List<XboxGame> {
        val body = get("https://apps.microsoft.com/api/Reco/GetComputedProductsList?listName=TopTrending&pgNo=1&noItems=12&filteredCategories=AllProducts&mediaType=Games&gl=${Xodus.market()}&hl=en-us&exp=0")
        return products(objects(body.optJSONArray("productsList")).map { it.optString("productId") }.take(24), access)
    }

    internal fun product(json: JSONObject, access: Map<String, String>, market: String = Xodus.market(), cloud: XboxCloudCatalog? = null): XboxGame? {
        val id = json.optString("ProductId").uppercase()
        if (!productId.matches(id) || json.optString("ProductType") != "Game") return null
        val localized = objects(json.optJSONArray("LocalizedProperties")).firstOrNull() ?: return null
        val title = localized.optString("ProductTitle").takeIf { it.isNotBlank() } ?: return null
        val packages = objects(json.optJSONArray("DisplaySkuAvailabilities")).flatMap {
            objects(it.optJSONObject("Sku")?.optJSONObject("Properties")?.optJSONArray("Packages"))
        }
        val pc = packages.filter { pkg -> objects(pkg.optJSONArray("PlatformDependencies")).any { it.optString("PlatformName") == "Windows.Desktop" } }
        val installable = pc.any { it.optString("PackageFormat").equals("MSIXVC", true) }
        val streams = cloud?.streams(id, access[id].orEmpty()) == true
        if (!installable && !streams) return null
        val images = objects(localized.optJSONArray("Images"))
        fun image(vararg kinds: String): String? = kinds.firstNotNullOfOrNull { purpose ->
            images.firstOrNull { it.optString("ImagePurpose") == purpose }?.optString("Uri")?.let { raw ->
                val url = if (raw.startsWith("//")) "https:$raw" else raw
                runCatching { java.net.URI(url) }.getOrNull()?.takeIf {
                    it.scheme == "https" && it.host in setOf("store-images.s-microsoft.com", "store-images.microsoft.com") && it.userInfo == null && it.port == -1
                }?.toString()
            }
        }
        return XboxGame(id, title, image("SuperHeroArt", "Hero", "BoxArt"), image("TitledPoster", "Poster", "BoxArt"),
            localized.optString("PublisherName"), localized.optString("DeveloperName"), localized.optString("ProductDescription"),
            if (installable) pc.maxOfOrNull { it.optLong("MaxDownloadSizeInBytes") } ?: 0L else 0L, market, access[id].orEmpty(), installable, streams)
    }

    fun objects(array: JSONArray?): List<JSONObject> = if (array == null) emptyList() else
        (0 until array.length()).mapNotNull { array.optJSONObject(it) }

    private fun get(url: String): JSONObject = JSONObject(fetch(url))
    private fun getArray(url: String): JSONArray = JSONArray(fetch(url))

    private fun fetch(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000; connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Bannerlator")
            check(connection.responseCode == 200) { "Microsoft Store could not be reached. Try again." }
            val text = connection.inputStream.use { it.readBytes() }.also { require(it.size <= 16_000_000) }
            return String(text, Charsets.UTF_8)
        } catch (e: java.io.IOException) {
            error("Microsoft Store could not be reached. Try again.")
        } finally { connection.disconnect() }
    }
}
