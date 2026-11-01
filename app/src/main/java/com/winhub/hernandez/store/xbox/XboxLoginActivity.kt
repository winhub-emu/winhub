package com.winhub.hernandez.store.xbox

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Microsoft sign-in for the Xbox store (ported from DroidDeck, GPL-3.0). It drives
 * `xodus-cli --external-webview-stdio login` in an isolated WebView: the page bridge only talks to the
 * allowed Microsoft/Xbox HTTPS origins in the main frame, and cookies are cleared when an attempt starts. The
 * Microsoft session the sign-in leaves is kept for the cloud player (XboxCloudActivity), which shares this
 * process (":xboxlogin") and its WebView data directory; store sign-out deletes that data. The other stores'
 * web logins live elsewhere and are never touched.
 */
class XboxLoginActivity : ComponentActivity() {
    private lateinit var host: LinearLayout
    private lateinit var status: TextView
    private var view: WebView? = null
    @Volatile private var child: Xodus.Child? = null
    @Volatile private var session = 0L
    @Volatile private var closed = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        runCatching { WebView.setDataDirectorySuffix(WEBVIEW_SUFFIX) }
        super.onCreate(savedInstanceState)
        host = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        status = TextView(this).apply { setTextColor(Color.WHITE); text = "Preparing Microsoft sign-in…"; setPadding(24, 16, 24, 16) }
        host.addView(status)
        host.addView(Button(this).apply { text = "Close"; setOnClickListener { finish() } })
        setContentView(host)
        if (android.os.Build.VERSION.SDK_INT < 28 || !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            status.text = "Update Android System WebView to sign in to Xbox."
            return
        }
        CookieManager.getInstance().removeAllCookies { android.webkit.WebStorage.getInstance().deleteAllData(); begin() }
    }

    private fun begin() {
        if (closed) return
        Thread({
            if (closed) return@Thread
            try {
                val accountGeneration = XboxCredentials.lifecycle(applicationContext) { it }
                Xodus.start(applicationContext, listOf("--external-webview-stdio", "login"), credentialGeneration = accountGeneration).use { process ->
                    child = process
                    if (closed) return@use
                    Xodus.diagnostics(applicationContext, process)
                    val timer = Thread({
                        runCatching { if (!process.process.waitFor(10, TimeUnit.MINUTES)) process.process.destroy() }
                    }, "xbox-login-timeout").apply { start() }
                    var ready = false
                    var latest = 0L
                    val input = process.process.inputStream.buffered()
                    while (!closed) {
                        val line = try { XboxCredentials.readLine(input, 131_072) } catch (e: java.io.EOFException) { break }
                        if (!line.startsWith("NEXUS_AUTH_V1 ")) continue
                        val frame = JSONObject(line.removePrefix("NEXUS_AUTH_V1 "))
                        when (frame.getString("type")) {
                            "ready" -> {
                                check(!ready && frame.getInt("version") == 1)
                                ready = true
                                process.send(JSONObject().put("type", "hello").put("version", 1))
                            }
                            "open" -> {
                                val next = frame.getLong("session")
                                check(ready && next > latest && next <= 32 && trusted(frame.getString("url")))
                                latest = next; session = next
                                runOnUiThread { if (!closed) open(frame) }
                            }
                            "close" -> {
                                check(frame.getLong("session") == session)
                                session = 0L
                                runOnUiThread { disposeView(); status.text = "Completing Microsoft sign-in…" }
                            }
                            "context" -> {
                                check(frame.getLong("session") == session)
                                val context = frame.getString("context")
                                check(context.length <= 4096)
                                val message = JSONObject().put("type", "callback").put("value", JSONObject()
                                    .put("name", "CloudExperienceHost.getContext")
                                    .put("args", org.json.JSONArray(listOf("CloudExperienceHost", "TokenBroker", "TokenBroker", "{\"PrivatePropertyBag\":1,\"PasswordlessConnect\":1,\"PreferAssociate\":1,\"ChromelessUI\":0}")))
                                    .put("context", context))
                                val active = session
                                runOnUiThread { if (active == session && trusted(view?.url.orEmpty())) view?.evaluateJavascript(
                                    "globalThis['CloudExperienceHost.Bridge.dispatchMessage'](${JSONObject.quote(message.toString())})", null) }
                            }
                            else -> error("Unsupported Xbox sign-in response")
                        }
                    }
                    check(process.process.waitFor(5, TimeUnit.SECONDS) && process.process.exitValue() == 0 && ready && latest > 0 && session == 0L)
                    timer.interrupt()
                }
                child = null
                if (closed) return@Thread
                val account = Xodus.run(applicationContext, listOf("account", "--json"), credentialGeneration = accountGeneration)
                check(account.optBoolean("stored"))
                XboxCredentials.lifecycle(this, accountGeneration) {
                    val record = XboxAccountStore.read(this) ?: error("Xbox credentials missing")
                    record.put("username", account.getString("username"))
                    XboxAccountStore.write(this, record)
                }
                XboxAccountStore.refreshGamertag(applicationContext, accountGeneration)
                XboxDiag.log(applicationContext, "sign-in complete")
                runOnUiThread { setResult(RESULT_OK); finish() }
            } catch (e: Exception) {
                child = null
                XboxDiag.log(applicationContext, "sign-in failed: ${e.javaClass.simpleName}")
                if (!closed) runOnUiThread { disposeView(); status.text = "Microsoft sign-in did not complete. Close this window and try again." }
            }
        }, "xbox-login").start()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun open(frame: JSONObject) {
        disposeView()
        val active = frame.getLong("session")
        val web = WebView(this)
        view = web
        web.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            allowFileAccess = false; allowContentAccess = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; MSAppHost/3.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.102 Safari/537.36 Edge/18.26100"
        }
        WebViewCompat.addWebMessageListener(web, "bannerlatorXbox", origins) { _, message, origin, mainFrame, _ ->
            val payload = message.data
            if (mainFrame && trusted(origin.toString()) && trusted(web.url.orEmpty()) && active == session && payload != null && payload.toByteArray().size <= 65_536) {
                runCatching { child?.send(JSONObject().put("type", "ipc").put("session", active).put("payload", payload)) }
            }
        }
        WebViewCompat.addDocumentStartJavaScript(web,
            "if(window===window.top){Object.defineProperty(globalThis.external,'notify',{value:p=>{if(typeof p==='string')bannerlatorXbox.postMessage(p)},configurable:false,writable:false})}", origins)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = !trusted(request.url.toString())
            override fun onPageFinished(view: WebView, url: String) {
                if (!trusted(url)) { finish(); return }
                status.text = "Microsoft sign-in"
                val uri = Uri.parse(url)
                if (uri.host == "login.live.com" && uri.path == "/ppsecure/post.srf") {
                    view.evaluateJavascript("JSON.stringify(globalThis.ServerData ?? null)") { raw ->
                        val payload = runCatching { org.json.JSONArray("[$raw]").getString(0) }.getOrNull()
                        if (active == session && payload != null && payload != "null" && payload.length <= 65_536) runCatching {
                            child?.send(JSONObject().put("type", "ipc").put("session", active).put("payload", payload))
                        }
                    }
                }
            }
        }
        host.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        val headers = frame.getJSONObject("headers")
        val values = headers.keys().asSequence().associateWith { key ->
            require(key in allowedHeaders)
            headers.getString(key).also { value -> require(value.length <= 4096 && value.all { it in ' '..'~' }) }
        }
        web.loadUrl(frame.getString("url"), values)
    }

    private fun disposeView() { view?.let { host.removeView(it); it.stopLoading(); it.destroy() }; view = null }
    override fun onDestroy() {
        closed = true
        val current = child; child = null
        Thread({ runCatching { current?.close() } }, "xbox-login-close").start()
        disposeView()
        CookieManager.getInstance().flush()
        super.onDestroy()
    }

    companion object {
        const val PROCESS_SUFFIX = ":xboxlogin"
        const val WEBVIEW_SUFFIX = "xboxlogin"

        /** True in the sign-in process (BannerlatorApp skips its start-up work there). */
        fun isLoginProcess(): Boolean = runCatching {
            val name = if (android.os.Build.VERSION.SDK_INT >= 28) android.app.Application.getProcessName()
                else java.io.File("/proc/self/cmdline").readText().trimEnd('\u0000')
            name.endsWith(PROCESS_SUFFIX)
        }.getOrDefault(false)

        private val hosts = setOf("login.live.com", "account.live.com", "login.microsoftonline.com", "login.microsoft.com", "account.microsoft.com", "sisu.xboxlive.com")
        private val origins = hosts.map { "https://$it" }.toSet()
        private val allowedHeaders = setOf("cxh-capabilities", "cxh-correlationid", "cxh-msabinaryversion", "cxh-identityclientbinaryversion", "cxh-osversioninfo", "cxh-platform", "cxh-protocol", "cxh-source", "hostapp")
        internal fun trusted(value: String): Boolean = runCatching {
            val url = java.net.URI(value)
            url.scheme == "https" && url.host in hosts && url.port == -1 && url.userInfo == null
        }.getOrDefault(false)
    }
}
