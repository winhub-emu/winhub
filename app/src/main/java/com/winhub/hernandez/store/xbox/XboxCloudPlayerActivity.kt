package com.winhub.hernandez.store.xbox

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Bannerlator's own Xbox Cloud Gaming player: signs in with the store account (XboxCloudSession, no web
 * sign-in), starts the cloud session and does the signalling here, and runs the WebRTC client (Greenlight's
 * player, tools/xcloud-player) in a local page. The page never sees a token. Falls back to Microsoft's web
 * player when this account cannot stream the game this way.
 */
class XboxCloudPlayerActivity : ComponentActivity() {
    private lateinit var root: FrameLayout
    private var web: WebView? = null
    private var notice: LinearLayout? = null
    private val worker = Executors.newSingleThreadExecutor()
    private val signalling = Executors.newCachedThreadPool()
    @Volatile private var session: XboxCloudSession.Session? = null
    @Volatile private var sessionPath: String? = null
    @Volatile private var titleId: String? = null
    @Volatile private var closed = false
    @Volatile private var webUrl: String? = null
    @Volatile private var settings = XboxCloudSettings()
    @Volatile private var sessionSignals: XboxCloudSignalling? = null
    /** The settings this stream started with (stream-start settings changed since then need a restart). */
    @Volatile private var startedWith: XboxCloudSettings? = null
    private var menu: androidx.compose.ui.platform.ComposeView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideBars()
        watchBars()
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)
        val requested = intent.getStringExtra("product").orEmpty()
        showNotice("Signing in to Xbox Cloud Gaming…", withButtons = false)
        worker.execute {
            val result = runCatching {
                val account = XboxAccountStore.signedInAs(applicationContext) ?: error("Sign in to the Xbox store first.")
                // A PC game whose console edition streams (one card in the library) streams that edition.
                val cached = XboxLibraryCache.read(applicationContext, account, Xodus.market())?.games?.firstOrNull { it.id == requested }
                val product = cached?.cloudId ?: requested
                webUrl = (cached ?: XboxGame(requested, intent.getStringExtra("title").orEmpty())).cloudUrl
                val active = XboxCloudSession.get(applicationContext, account)
                val title = XboxCloudSession.titles(active)[product] ?: error("This game can't be streamed with this account here.")
                settings = XboxCloudSettings.load(applicationContext)
                startedWith = settings
                session = active.forStream(settings.region, settings.osName); titleId = title
                XboxDiag.log(applicationContext, "cloud player: $product -> title $title, region ${java.net.URI(session!!.baseUri).host}, " +
                    "transfer=${active.userToken != null}, settings ${settings.toJson()}")
            }
            runOnUiThread {
                if (closed) return@runOnUiThread
                result.onSuccess { openPlayer() }.onFailure {
                    XboxDiag.log(applicationContext, "cloud player not started: ${it.javaClass.simpleName}: ${it.message?.take(160)}")
                    showNotice(it.message ?: "Xbox Cloud Gaming could not start.", withButtons = true)
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun openPlayer() {
        val loader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        val view = WebView(this)
        web = view
        view.settings.apply {
            javaScriptEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
        }
        view.setBackgroundColor(Color.BLACK)
        // The Gamepad API only sees controllers while the WebView has input focus.
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.addJavascriptInterface(Bridge(), "BLCloud")
        view.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
        }
        view.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                android.util.Log.d("XboxCloudPlayer", "console ${message.messageLevel()}: ${message.message().take(300)}")
                return true
            }
        }
        root.addView(view, 0, FrameLayout.LayoutParams(-1, -1))
        notice?.let { root.removeView(it) }
        notice = null
        view.requestFocus()
        view.loadUrl("https://appassets.androidplatform.net/assets/xcloud/player.html")
    }

    private inner class Bridge {
        @JavascriptInterface
        fun call(id: Int, op: String, arg: String) {
            signalling.execute {
                val reply = runCatching { handle(op, arg) }.getOrElse { XboxCloudSession.Reply(0, it.javaClass.simpleName) }
                if (reply.status !in 200..299) XboxDiag.log(applicationContext, "cloud player $op: ${reply.status} ${reply.body.take(300)}")
                else if (op != "state" && op != "keepalive") XboxDiag.log(applicationContext, "cloud player $op: ${reply.status}")
                runOnUiThread { web?.evaluateJavascript("window.__blResolve($id, ${reply.status}, ${JSONObject.quote(reply.body)})", null) }
            }
        }

        @JavascriptInterface
        fun status(text: String) { android.util.Log.i("XboxCloudPlayer", "status: ${text.take(120)}") }

        @JavascriptInterface
        fun playing() {
            XboxDiag.log(applicationContext, "cloud player streaming")
            runOnUiThread { web?.requestFocus() }
        }

        @JavascriptInterface
        fun settings(): String = pageSettings(this@XboxCloudPlayerActivity.settings)

        @JavascriptInterface
        fun log(text: String) { XboxDiag.log(applicationContext, "cloud player page: ${text.take(240)}") }

        @JavascriptInterface
        fun failed(stage: String, detail: String) {
            XboxDiag.log(applicationContext, "cloud player failed at ${stage.take(40)}: ${detail.take(300)}")
            runOnUiThread { if (!closed) showNotice("The stream could not start ($stage).", withButtons = true) }
        }
    }

    private fun handle(op: String, arg: String): XboxCloudSession.Reply {
        val active = session ?: error("no session")
        val signals = sessionSignals ?: XboxCloudSignalling(active, titleId ?: error("no title"), settings).also { sessionSignals = it }
        return signals.op(op, arg).also { sessionPath = signals.path }
    }

    private fun showNotice(text: String, withButtons: Boolean) {
        notice?.let { root.removeView(it) }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(if (withButtons) Color.argb(230, 0, 0, 0) else Color.TRANSPARENT)
        }
        box.addView(TextView(this).apply { setTextColor(Color.WHITE); textSize = 18f; gravity = Gravity.CENTER; this.text = text; setPadding(32, 16, 32, 16) })
        if (withButtons) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            (webUrl ?: intent.getStringExtra("webUrl"))?.let { url ->
                row.addView(Button(this).apply { this.text = "Open in Microsoft's player"; setOnClickListener { startActivity(XboxCloudActivity.intent(this@XboxCloudPlayerActivity, url)); finish() } })
            }
            row.addView(Button(this).apply { this.text = "Close"; setOnClickListener { finish() } })
            box.addView(row)
        }
        notice = box
        root.addView(box, FrameLayout.LayoutParams(-1, -1))
    }

    private val rehideBars = Runnable { if (window.decorView.hasWindowFocus()) hideBars() }

    /**
     * Bars that come back while the player has focus (return from recents or another app, a swipe) are hidden
     * again after a moment; focus alone missed the return from the background (10-24).
     */
    private fun watchBars() {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            if (insets.isVisible(WindowInsetsCompat.Type.navigationBars()) || insets.isVisible(WindowInsetsCompat.Type.statusBars())) {
                v.removeCallbacks(rehideBars)
                v.postDelayed(rehideBars, 1500)
            }
            androidx.core.view.ViewCompat.onApplyWindowInsets(v, insets)
        }
    }

    override fun onResume() {
        super.onResume()
        hideBars()
    }

    private fun hideBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    private var guideDown = false
    private var guideDownAt = 0L
    private var lastGuideUp = 0L
    private var pendingNexus: Runnable? = null
    /** The release of the press that opened the menu must not close it again. */
    private var swallowGuideUp = false

    /** The Guide (Mode) button. */
    private fun isGuideKey(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_BUTTON_B) return false
        if (code != KeyEvent.KEYCODE_BUTTON_MODE) return false
        // Same rule as the game session: never the Back Android makes out of an unhandled B.
        return !(code == KeyEvent.KEYCODE_BACK && ((event.flags and KeyEvent.FLAG_FALLBACK) != 0 || event.scanCode in 0x130..0x13f))
    }

    /**
     * One press presses the Xbox button in the stream (the game's Xbox menu), sent once a second press can no
     * longer follow; two quick presses, or holding the button, open the player menu instead.
     */
    private fun guideKey(event: KeyEvent): Boolean {
        if (!isGuideKey(event)) return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount == 0) {
                guideDown = true; guideDownAt = event.eventTime
                val pending = pendingNexus
                if (pending != null && event.eventTime - lastGuideUp <= DOUBLE_PRESS_MS) {
                    root.removeCallbacks(pending); pendingNexus = null; guideDown = false
                    swallowGuideUp = true
                    openMenu()
                }
            } else if (guideDown && event.eventTime - guideDownAt >= 600) {
                guideDown = false
                swallowGuideUp = true
                openMenu()
            }
        } else if (event.action == KeyEvent.ACTION_UP && guideDown) {
            guideDown = false
            lastGuideUp = event.eventTime
            val tap = Runnable { pendingNexus = null; pressNexus(1); root.postDelayed({ pressNexus(0) }, 150) }
            pendingNexus = tap
            root.postDelayed(tap, DOUBLE_PRESS_MS)
        }
        return true
    }

    private fun pressNexus(value: Int) {
        web?.evaluateJavascript("window.__blButton && window.__blButton('Nexus', $value)", null)
    }

    // Back (the Pocket FIT's back button, B the stream did not take, or touch back) opens the player menu, so a
    // stray press never ends a stream; Back again closes it. With an error notice up, Back leaves.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (menu != null) {
            // Guide closes the menu; Back goes up one page, then closes it.
            if (isGuideKey(event)) {
                if (event.action == KeyEvent.ACTION_UP) { if (swallowGuideUp) swallowGuideUp = false else closeMenu() }
                return true
            }
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) { if (menuPage.isNotEmpty()) menuPage = "" else closeMenu() }
                return true
            }
            return super.dispatchKeyEvent(event)
        }
        if (notice == null && guideKey(event)) return true
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) { if (notice == null) openMenu() else finish() }
            return true
        }
        // Controller buttons belong to the stream: the page reads them through the Gamepad API (fed by this
        // dispatch), and reporting them handled stops Android adding its fallback key for an unhandled button
        // (B became Home on the Pocket FIT).
        val handled = super.dispatchKeyEvent(event)
        val fromController = event.source and android.view.InputDevice.SOURCE_GAMEPAD == android.view.InputDevice.SOURCE_GAMEPAD ||
            event.source and android.view.InputDevice.SOURCE_JOYSTICK == android.view.InputDevice.SOURCE_JOYSTICK
        return handled || (notice == null && web != null && fromController)
    }

    /** The open player-menu page: "" = the list, else a settings section. */
    private var menuPage by androidx.compose.runtime.mutableStateOf("")

    /**
     * The player menu, like the Wine games' side menu: Resume, Xbox menu, Screen effects, Stream quality, Stats and
     * sound, Leave; each settings entry opens its own page (screen effects, stats and sound change the stream live).
     */
    private fun openMenu() {
        if (menu != null || web == null) return
        web?.evaluateJavascript("window.__blInput && window.__blInput(false)", null)
        menuPage = ""
        val regions = session?.regionList?.map { it.name }.orEmpty()
        val view = androidx.compose.ui.platform.ComposeView(this)
        view.setContent {
            com.winhub.hernandez.ui.theme.WinlatorTheme {
                var current by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(settings) }
                // The main list returns to the entry used last; each settings page to its last row (the panel restores it).
                val entries = androidx.compose.runtime.remember { List(6) { androidx.compose.ui.focus.FocusRequester() } }
                androidx.compose.runtime.LaunchedEffect(menuPage) {
                    if (menuPage.isEmpty()) runCatching { entries[lastMainEntry.coerceIn(0, 5)].requestFocus() }
                }
                val cs = androidx.compose.material3.MaterialTheme.colorScheme
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                    androidx.compose.foundation.layout.Column(
                        androidx.compose.ui.Modifier.fillMaxHeight().width(400.dp).background(cs.surface.copy(alpha = 0.95f))
                            .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(vertical = 12.dp),
                    ) {
                        androidx.compose.material3.Text(intent.getStringExtra("title").orEmpty().ifEmpty { "Xbox Cloud Gaming" },
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = cs.onSurface,
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        if (menuPage.isEmpty()) {
                            MenuEntry("Resume", "Back to the game", entries[0], 0) { closeMenu() }
                            MenuEntry("Xbox menu", "The game's Xbox menu (also: Guide once, or View + Menu)", entries[1], 1) {
                                closeMenu(); web?.evaluateJavascript("window.__blNexus && window.__blNexus()", null)
                            }
                            MenuEntry("Screen effects", "Sharpen, colour, deband, FXAA, CRT, NTSC, Toon, fps cap", entries[2], 2) { menuPage = "picture" }
                            MenuEntry("Stream quality", "Resolution, region, language, codec, bitrate (next stream)", entries[3], 3) { menuPage = "stream" }
                            MenuEntry("Performance overlay and sound", "Overlay level and layout, volume, controller rumble", entries[4], 4) { menuPage = "overlay" }
                            MenuEntry("Leave", "End the stream", entries[5], 5) { finish() }
                            androidx.compose.material3.Text("Guide twice opens this menu; Back or Guide closes it.",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                                modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        } else {
                            MenuEntry("‹  Back", null) { menuPage = "" }
                            if (menuPage == "stream") MenuEntry("Restart the stream with these settings",
                                "Ends this cloud session and starts a new one; the game restarts, unsaved progress is lost.") { restartStream() }
                            val page = menuPage
                            XboxCloudSettingsPanel(current, regions, live = true, section = page, restoreRow = lastRow[page] ?: 0,
                                onRowFocused = { lastRow[page] = it }, startedWith = startedWith) { changed ->
                                current = changed
                                settings = changed
                                XboxCloudSettings.save(this@XboxCloudPlayerActivity, changed)
                                web?.evaluateJavascript("window.__blApply && window.__blApply(${JSONObject.quote(pageSettings(changed))})", null)
                            }
                        }
                    }
                }
            }
        }
        menu = view
        root.addView(view, FrameLayout.LayoutParams(-1, -1))
        view.requestFocus()
    }

    /** The settings as the page reads them, plus the stream server's name for the full overlay. */
    private fun pageSettings(s: XboxCloudSettings): String = JSONObject(s.toJson())
        .put("server", session?.let { active -> active.regionList.firstOrNull { it.baseUri == active.baseUri }?.name } ?: "").toString()

    /** The entry the main menu list returns to, and each settings page's last row. */
    private var lastMainEntry = 0
    private val lastRow = mutableMapOf<String, Int>()

    @androidx.compose.runtime.Composable
    private fun MenuEntry(title: String, note: String?, focus: androidx.compose.ui.focus.FocusRequester? = null, index: Int = -1, onClick: () -> Unit) =
        XboxMenuEntry(title, note, focus, { if (index >= 0) lastMainEntry = index }, onClick)

    /** Stream-quality changes need a new cloud session: end this one, then open the player again. */
    private fun restartStream() {
        val active = session; val path = sessionPath
        sessionPath = null
        closeMenu()
        showNotice("Restarting the stream…", withButtons = false)
        Thread({
            if (active != null && path != null) runCatching { XboxCloudSession.request(active, "DELETE", "/$path") }
                .onSuccess { XboxDiag.log(applicationContext, "cloud player restart: session closed ${it.status}") }
            runOnUiThread { if (!isFinishing) { startActivity(XboxCloudPlayerActivity.intent(this, intent.getStringExtra("product").orEmpty(), intent.getStringExtra("title").orEmpty())); finish() } }
        }, "xcloud-restart").start()
    }

    private fun closeMenu() {
        menu?.let { root.removeView(it) }
        menu = null
        web?.evaluateJavascript("window.__blInput && window.__blInput(true)", null)
        web?.requestFocus()
    }

    override fun onDestroy() {
        closed = true
        web?.let { it.evaluateJavascript("window.__blStop && window.__blStop()", null); root.removeView(it); it.destroy() }
        web = null
        val active = session; val path = sessionPath
        if (active != null && path != null) Thread({
            runCatching { XboxCloudSession.request(active, "DELETE", "/$path") }
                .onSuccess { XboxDiag.log(applicationContext, "cloud player session closed: ${it.status}") }
        }, "xcloud-stop").start()
        worker.shutdownNow(); signalling.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val DOUBLE_PRESS_MS = 350L

        /** The opt-in native engine (frame generation) when chosen and supported, else this web-view player. */
        private fun playerClass(context: Context): Class<*> =
            if (XboxCloudSettings.load(context).playerEngine == "native" && XboxNativePlayerActivity.supported())
                XboxNativePlayerActivity::class.java else XboxCloudPlayerActivity::class.java

        fun intent(context: Context, game: XboxGame): Intent = Intent(context, playerClass(context))
            .putExtra("product", game.id).putExtra("title", game.title).putExtra("webUrl", game.cloudUrl)

        /** Stream an Xbox shortcut's game (launch method "Stream"); the player resolves the cloud edition. */
        fun intent(context: Context, productId: String, title: String): Intent = Intent(context, playerClass(context))
            .putExtra("product", productId.uppercase()).putExtra("title", title)

        /** Whether the cached Xbox library says this product streams (off the main thread: it reads the cache). */
        fun streams(context: Context, productId: String): Boolean = runCatching {
            val app = context.applicationContext
            val account = XboxAccountStore.signedInAs(app) ?: return false
            XboxLibraryCache.read(app, account, Xodus.market())?.games?.firstOrNull { it.id == productId.uppercase() }?.cloud == true
        }.getOrDefault(false)
    }
}
