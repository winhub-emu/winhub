package com.winhub.hernandez.store.xbox

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Xbox Cloud Gaming sign-in from the store's own account: Xodus' `cloud-token` turns the stored Microsoft
 * account into a streaming-service token (no web sign-in), kept in memory only until shortly before it
 * expires. The request shapes follow Greenlight / xal-node by unknownskl (MIT).
 */
internal object XboxCloudSession {
    class Region(val name: String, val baseUri: String, val isDefault: Boolean)

    class Session(val account: String, val token: String, val offering: String, val baseUri: String, val regionList: List<Region>,
                  /** Microsoft account token for a session's /connect step; null when the account could not get one. */
                  val userToken: String?, private val expiresAt: Long,
                  /** The os name sent with each request (it selects the stream resolution). */
                  val osName: String = "windows") {
        val fresh get() = System.currentTimeMillis() < expiresAt - 5 * 60_000L
        val regions get() = regionList.size

        /** This session against [regionName]'s server (Microsoft's default when empty or unknown), asking as [os]. */
        fun forStream(regionName: String, os: String): Session = Session(account, token, offering,
            regionList.firstOrNull { it.name == regionName }?.baseUri ?: baseUri, regionList, userToken, expiresAt, os)
    }

    @Volatile private var cached: Session? = null

    fun get(app: Context, account: String): Session {
        cached?.takeIf { it.account == account && it.fresh }?.let { return it }
        val json = Xodus.run(app, listOf("cloud-token"), timeout = 60)
        require(json.getInt("schema") == 1 && json.getString("account") == account)
        val array = json.getJSONArray("regions")
        val regions = (0 until array.length()).map { array.getJSONObject(it) }
            .map { Region(it.getString("name"), it.getString("baseUri"), it.optBoolean("isDefault")) }
        val default = regions.firstOrNull { it.isDefault } ?: regions.firstOrNull() ?: error("No Xbox Cloud Gaming region")
        val seconds = json.optLong("durationInSeconds").coerceIn(0, 24 * 3600)
        return Session(account, json.getString("gsToken"), json.getString("offering"), default.baseUri, regions,
            json.optString("userToken").takeIf { !json.isNull("userToken") && it.isNotEmpty() }, System.currentTimeMillis() + seconds * 1000).also { cached = it }
    }

    fun clear() { cached = null; titleIds = null }

    @Volatile private var titleIds: Pair<String, Map<String, String>>? = null

    /** Store product id -> cloud title id for the titles this account may stream, from the streaming service itself. */
    fun titles(session: Session): Map<String, String> {
        titleIds?.takeIf { it.first == session.account }?.let { return it.second }
        val reply = request(session, "GET", "/v2/titles")
        check(reply.status == 200) { "Xbox Cloud Gaming answered ${reply.status}" }
        val results = JSONObject(reply.body).optJSONArray("results") ?: JSONArray()
        val map = (0 until results.length()).mapNotNull { index ->
            val row = results.optJSONObject(index) ?: return@mapNotNull null
            val product = row.optJSONObject("details")?.optString("productId")?.uppercase().orEmpty()
            val title = row.optString("titleId")
            if (product.matches(Regex("[A-Z0-9]{12}")) && title.isNotBlank() && title.length <= 64) product to title else null
        }.toMap()
        titleIds = session.account to map
        return map
    }

    class Reply(val status: Int, val body: String)

    /** One request to the streaming service as Microsoft's web player makes it (Greenlight's shapes). */
    fun request(session: Session, method: String, path: String, body: String? = null): Reply {
        val connection = URL(session.baseUri + path).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000; connection.readTimeout = 30_000
            connection.requestMethod = method
            connection.setRequestProperty("Authorization", "Bearer ${session.token}")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-Gssv-Client", "XboxComBrowser")
            connection.setRequestProperty("X-MS-Device-Info", deviceInfo(session.osName))
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { it.readBytes() }?.also { require(it.size <= 16_000_000) } ?: ByteArray(0)
            return Reply(status, String(text, Charsets.UTF_8))
        } finally { connection.disconnect() }
    }

    private fun deviceInfo(os: String) = JSONObject()
        .put("appInfo", JSONObject().put("env", JSONObject().put("clientAppId", "www.xbox.com").put("clientAppType", "browser")
            .put("clientAppVersion", "21.1.98").put("clientSdkVersion", "8.5.3").put("httpEnvironment", "prod").put("sdkInstallId", "")))
        .put("dev", JSONObject()
            .put("hw", JSONObject().put("make", "Microsoft").put("model", "unknown").put("sdktype", "web"))
            .put("os", JSONObject().put("name", os).put("ver", "22631.2715").put("platform", "desktop"))
            .put("displayInfo", JSONObject().put("dimensions", JSONObject().put("widthInPixels", 1920).put("heightInPixels", 1080))
                .put("pixelDensity", JSONObject().put("dpiX", 2).put("dpiY", 2)))
            .put("browser", JSONObject().put("browserName", "chrome").put("browserVersion", "119.0")))
        .toString()
}
