package com.winhub.hernandez.store.xbox

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Native Xbox Cloud Gaming player (opt-in, Android 10+): Google's WebRTC library instead of the web page, the
 * xCloud channels ported from Greenlight (XboxNativeProtocol), controller from Android input events, and the
 * picture through Bannerlator's Vulkan compositor (XboxNativeVideo) so Win-FG or LSFG can generate frames.
 * Sign-in and session requests are the web player's (XboxCloudSession, XboxCloudSignalling).
 */
class XboxNativePlayerActivity : ComponentActivity() {
    private lateinit var root: FrameLayout
    private lateinit var surfaceView: SurfaceView
    private lateinit var status: TextView
    private lateinit var overlay: TextView
    private val worker = Executors.newSingleThreadExecutor()
    private val ticker = Executors.newSingleThreadScheduledExecutor()
    private val pad = XboxNativePad()
    private var eglBase: EglBase? = null
    private var factory: PeerConnectionFactory? = null
    private var pc: PeerConnection? = null
    private var protocol: XboxNativeProtocol? = null
    private var video: XboxNativeVideo? = null
    @Volatile private var signalling: XboxCloudSignalling? = null
    @Volatile private var settings = XboxCloudSettings()
    @Volatile private var closed = false
    /** The stream reached the screen at least once (a later failure is a lost connection, not a failed start). */
    @Volatile private var connectedOnce = false
    /** The session was ended because the player stayed in the background (Android freezes the app soon after). */
    @Volatile private var endedInBackground = false
    private var backgroundEnd: Runnable? = null
    private var notice: androidx.compose.ui.platform.ComposeView? = null
    @Volatile private var regionNames = emptyList<String>()
    @Volatile private var serverName: String? = null
    private val candidates = mutableListOf<IceCandidate>()
    private val gathered = CountDownLatch(1)
    private var menu: androidx.compose.ui.platform.ComposeView? = null
    private var menuPage by androidx.compose.runtime.mutableStateOf("")
    private var lastMainEntry = 0
    private val lastRow = mutableMapOf<String, Int>()
    /** The settings this stream started with (stream-start settings changed since then need a restart). */
    private val startedWith by lazy { XboxCloudSettings.load(this) }
    /**
     * The picture's route right now: true = zero-copy (decoder straight onto the screen), false = WebRTC's texture
     * through the Vulkan compositor. Zero-copy whenever it is chosen and frame generation is off; switched live.
     */
    @Volatile private var zeroCopy = false
    /** The SurfaceView's surface while it exists (UI thread). */
    private var screen: android.view.Surface? = null
    private var screenW = 0
    private var screenH = 0
    @Volatile private var zeroCopyFrames = 0L
    @Volatile private var decoders: org.webrtc.BannerlatorLowLatencyDecoderFactory? = null
    private var lastZeroCopyFrames = 0L
    /** The release of the Guide press that opened the menu must not close it again. */
    private var swallowGuideUp = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideBars()
        watchBars()
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        surfaceView = SurfaceView(this)
        root.addView(surfaceView, FrameLayout.LayoutParams(-1, -1))
        status = TextView(this).apply { setTextColor(Color.WHITE); textSize = 16f; gravity = Gravity.CENTER }
        root.addView(status, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = 120 })
        overlay = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 12f; typeface = android.graphics.Typeface.MONOSPACE
            setBackgroundColor(Color.argb(140, 0, 0, 0)); setPadding(12, 6, 12, 6); visibility = android.view.View.GONE
        }
        root.addView(overlay, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { topMargin = 16; rightMargin = 16 })
        setContentView(root)
        settings = XboxCloudSettings.load(this)
        applyDisplayRate(settings.fpsTarget)
        applyOverlay()

        val egl = EglBase.create().also { eglBase = it }
        val nativeVideo = XboxNativeVideo(this, egl) { log(it) }.also { video = it }
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {}
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                screen = holder.surface; screenW = width; screenH = height
                // Zero-copy: the decoder owns this surface (back on it after a stand-in); the Vulkan compositor must not connect to it.
                if (zeroCopy) { decoders?.setZeroCopySurface(holder.surface); return }
                @Suppress("DEPRECATION")
                nativeVideo.setSurface(holder.surface, width, height, windowManager.defaultDisplay.refreshRate)
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                screen = null
                if (zeroCopy) decoders?.setZeroCopySurface(null) else nativeVideo.setSurface(null, 0, 0, 60f)
            }
        })
        show("Signing in to Xbox Cloud Gaming…")
        worker.execute { runCatching { run() }.onFailure { fail("start", it.message ?: it.javaClass.simpleName) } }
    }

    private fun log(text: String) = XboxDiag.log(applicationContext, "native player: ${text.take(240)}")
    private fun show(text: String) = runOnUiThread { status.text = text; status.visibility = if (text.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE }

    private fun fail(stage: String, detail: String) {
        log("failed at $stage: $detail")
        if (closed) return
        if (connectedOnce) showNotice("The connection to the cloud console was lost",
            "The stream stopped reaching this device ($detail). Restart the stream to play again: the game starts over, " +
                "unsaved progress is lost.", webPlayer = false)
        else showNotice("The native stream could not start ($stage)",
            "$detail\n\nYou can try again, or use the web player instead (Xbox › Profile › Cloud gaming settings › Player engine).",
            webPlayer = true)
    }

    /** One popup at a time, in the app's dialog style; A or Enter picks the selected button, Back closes the player. */
    private fun showNotice(title: String, text: String, webPlayer: Boolean) = runOnUiThread {
        if (notice != null || isFinishing || isDestroyed) return@runOnUiThread
        closeMenu()
        show("")
        // The stream is over: stale numbers must not stay on screen.
        overlayOn = false
        overlay.visibility = android.view.View.GONE
        val view = androidx.compose.ui.platform.ComposeView(this)
        view.setContent {
            com.winhub.hernandez.ui.theme.WinlatorTheme {
                val first = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
                androidx.compose.runtime.LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos { }; runCatching { first.requestFocus() } }
                @androidx.compose.runtime.Composable
                fun NoticeButton(label: String, focus: androidx.compose.ui.focus.FocusRequester?, onClick: () -> Unit) =
                    androidx.compose.material3.TextButton(onClick = onClick,
                        modifier = androidx.compose.ui.Modifier.let { if (focus != null) it.focusRequester(focus) else it }
                            .onKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.ButtonA) { onClick(); true } else false }) {
                        androidx.compose.material3.Text(label)
                    }
                com.winhub.hernandez.ui.screens.OutlinedAlertDialog(
                    onDismissRequest = { finish() },
                    properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
                    title = { androidx.compose.material3.Text(title) },
                    text = { androidx.compose.material3.Text(text) },
                    confirmButton = {
                        androidx.compose.foundation.layout.Row {
                            NoticeButton(if (connectedOnce) "Restart the stream" else "Try again", first) { restartStream() }
                            if (webPlayer) NoticeButton("Open the web player", null) {
                                startActivity(Intent(this@XboxNativePlayerActivity, XboxCloudPlayerActivity::class.java).putExtras(intent)); finish()
                            }
                        }
                    },
                    dismissButton = { NoticeButton("Close", null) { finish() } },
                )
            }
        }
        notice = view
        root.addView(view, FrameLayout.LayoutParams(-1, -1))
    }

    // ---- background ----

    override fun onStop() {
        super.onStop()
        if (closed || isChangingConfigurations || signalling == null || endedInBackground) return
        // Android freezes a background app about a minute after it leaves the screen; the connection then dies
        // without a goodbye. End the session cleanly before that and offer a restart on return.
        log("in the background")
        val end = Runnable { backgroundEnd = null; endInBackground() }
        backgroundEnd = end
        root.postDelayed(end, BACKGROUND_GRACE_MS)
    }

    override fun onStart() {
        super.onStart()
        backgroundEnd?.let { root.removeCallbacks(it); log("back before the stream was ended") }
        backgroundEnd = null
        if (endedInBackground && notice == null) showNotice("The stream ended while Bannerlator was in the background",
            "Cloud sessions are closed after ${BACKGROUND_GRACE_MS / 1000} s away, before Android pauses the app. Restart the " +
                "stream to play again: the game starts over, unsaved progress is lost.", webPlayer = false)
    }

    private fun endInBackground() {
        if (closed || endedInBackground) return
        endedInBackground = true
        closed = true
        log("ending the session: ${BACKGROUND_GRACE_MS / 1000} s in the background")
        val signals = signalling
        signalling = null
        ticker.shutdownNow()
        runOnUiThread { overlayOn = false; overlay.visibility = android.view.View.GONE }
        Thread({ signals?.stop()?.let { log("session closed: ${it.status}") } }, "xcloud-native-stop").start()
        runCatching { protocol?.close() }
        runCatching { pc?.close() }
    }

    // ---- session ----

    private fun run() {
        val account = XboxAccountStore.signedInAs(applicationContext) ?: error("Sign in to the Xbox store first.")
        val requested = intent.getStringExtra("product").orEmpty()
        val cached = XboxLibraryCache.read(applicationContext, account, Xodus.market())?.games?.firstOrNull { it.id == requested }
        val product = cached?.cloudId ?: requested
        val active = XboxCloudSession.get(applicationContext, account).forStream(settings.region, settings.osName)
        regionNames = active.regionList.map { it.name }
        serverName = active.regionList.firstOrNull { it.baseUri == active.baseUri }?.name
        val title = XboxCloudSession.titles(active)[product] ?: error("This game can't be streamed with this account here.")
        val signals = XboxCloudSignalling(active, title, settings).also { signalling = it }
        log("$product -> $title, region ${java.net.URI(active.baseUri).host}, frame gen ${settings.frameGen}")

        show("Starting the game on Xbox Cloud Gaming…")
        expect("start", signals.start())
        var connected = false
        var waited = 0
        while (!closed) {
            val state = JSONObject(expect("state", signals.op("state")).body.ifEmpty { "{}" })
            when (state.optString("state")) {
                "Provisioned" -> break
                "Failed" -> error("provision: ${state.optJSONObject("errorDetails")}")
                "ReadyToConnect" -> if (!connected) { connected = true; show("Signing in to the cloud console…"); expect("connect", signals.connect()) }
                "WaitingForResources" -> show("In the queue for a cloud console… ${waited / 1000} s")
                else -> show("Getting a cloud console ready… ${waited / 1000} s")
            }
            Thread.sleep(1000); waited += 1000
        }
        if (closed) return

        show("Connecting…")
        // Small network buffer: no playout delay, frames go to the decoder and screen as soon as they are complete.
        val trials = if (settings.smallBuffer) "WebRTC-ForcePlayoutDelay/min_ms:0,max_ms:0/WebRTC-ZeroPlayoutDelay/min_pacing:0ms,max_decode_queue_size:8/" else ""
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(applicationContext)
            .setFieldTrials(trials).createInitializationOptions())
        val egl = eglBase!!
        val zeroSurface = if (settings.zeroCopy && settings.frameGen == XboxNativeVideo.FG_OFF) {
            repeat(40) { if (!surfaceView.holder.surface.isValid) Thread.sleep(50) }
            surfaceView.holder.surface.takeIf { it.isValid }
        } else null
        if (zeroSurface != null) {
            // The compositor may already hold the screen; it must let go before the decoder takes it.
            val ready = CountDownLatch(1)
            runOnUiThread { zeroCopy = true; video?.setSurface(null, 0, 0, 60f); ready.countDown() }
            ready.await(2, TimeUnit.SECONDS)
        }
        log("latency: instant input ${settings.instantInput}, small buffer ${settings.smallBuffer}, fast decoder ${settings.fastDecoder}, " +
            "zero-copy ${zeroSurface != null}")
        val f = PeerConnectionFactory.builder()
            .setAudioDeviceModule(JavaAudioDeviceModule.builder(applicationContext).createAudioDeviceModule())
            .setVideoDecoderFactory(org.webrtc.BannerlatorLowLatencyDecoderFactory(egl.eglBaseContext, settings.fastDecoder, zeroSurface) { zeroCopyFrames++ }
                .also { decoders = it })
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .createPeerConnectionFactory().also { factory = it }
        val config = PeerConnection.RTCConfiguration(emptyList()).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN }
        val peer = f.createPeerConnection(config, observer) ?: error("WebRTC unavailable")
        pc = peer
        peer.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV))
        peer.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY))
        protocol = XboxNativeProtocol(peer, pad, { log(it) }, ::rumble) { runOnUiThread { finish() } }
        pad.onChanged = { if (settings.instantInput) protocol?.inputChanged() }

        val offer = await<SessionDescription> { done -> peer.createOffer(sdpObserver(done), MediaConstraints()) }
        val local = SessionDescription(offer.type, tweakOffer(offer.description))
        await<Unit> { done -> peer.setLocalDescription(sdpObserver(done), local) }
        val sdpReply = expect("sdp", signals.sdp(local.description))
        val answer = JSONObject(JSONObject(sdpReply.body).getString("exchangeResponse"))
        if (answer.has("status") && answer.optString("status") != "success") error("sdp answer: $answer")
        await<Unit> { done -> peer.setRemoteDescription(sdpObserver(done), SessionDescription(SessionDescription.Type.ANSWER, answer.getString("sdp"))) }

        gathered.await(3, TimeUnit.SECONDS)
        val local2 = synchronized(candidates) { candidates.toList() }.map { c ->
            JSONObject().put("candidate", c.sdp).put("sdpMid", c.sdpMid).put("sdpMLineIndex", c.sdpMLineIndex).put("usernameFragment", JSONObject.NULL).toString()
        }
        val iceReply = expect("ice", signals.ice(JSONArray(local2).toString()))
        val remote = JSONArray(JSONObject(iceReply.body).getString("exchangeResponse"))
        for (i in 0 until remote.length()) addRemoteCandidate(peer, remote.getJSONObject(i))
        log("signalling done: ${local2.size} local / ${remote.length()} remote candidates")
        ticker.scheduleWithFixedDelay({ runCatching { signals.op("keepalive") } }, 30, 30, TimeUnit.SECONDS)
        ticker.scheduleWithFixedDelay({ runCatching { updateOverlay() } }, 1, 1, TimeUnit.SECONDS)
    }

    private fun expect(stage: String, reply: XboxCloudSession.Reply): XboxCloudSession.Reply {
        if (reply.status !in 200..299) error("$stage: ${reply.status} ${reply.body.take(200)}")
        return reply
    }

    /** Opus stereo (Greenlight), H.264 profile order (Main first unless the user picked one) and the bitrate cap. */
    private fun tweakOffer(sdp: String): String {
        var out = sdp.replace("useinbandfec=1", "useinbandfec=1; stereo=1")
        val prefix = when (settings.codec) { "low" -> "420"; "normal" -> "42e"; else -> "4d" }
        val ids = Regex("a=fmtp:(\\d+).*profile-level-id=([0-9a-f]{6})").findAll(out).filter { it.groupValues[2].startsWith(prefix) }.map { it.groupValues[1] }.toList()
        if (ids.isNotEmpty()) out = out.split("\r\n").joinToString("\r\n") { line ->
            if (!line.startsWith("m=video")) line else {
                val parts = line.trim().split(" ")
                (parts.take(3) + ids + parts.drop(3).filter { it !in ids }).joinToString(" ")
            }
        }
        if (settings.bitrateMbps > 0) {
            val lines = out.split("\r\n").toMutableList()
            val start = lines.indexOfFirst { it.startsWith("m=video") }
            if (start >= 0) {
                var i = start + 1
                while (i < lines.size && (lines[i].startsWith("i=") || lines[i].startsWith("c="))) i++
                if (i < lines.size && lines[i].startsWith("b=AS:")) lines[i] = "b=AS:${settings.bitrateMbps * 1000}" else lines.add(i, "b=AS:${settings.bitrateMbps * 1000}")
                out = lines.joinToString("\r\n")
            }
        }
        return out
    }

    /** The server's candidates, plus the IPv4 behind a Teredo address (Greenlight's ice.ts / teredo.ts). */
    private fun addRemoteCandidate(peer: PeerConnection, c: JSONObject) {
        val line = c.optString("candidate").removePrefix("a=")
        if (line.isEmpty() || line == "end-of-candidates") return
        val mid = c.optString("sdpMid", "0"); val index = c.optString("sdpMLineIndex", "0").toIntOrNull() ?: 0
        val address = line.split(" ").getOrNull(4).orEmpty()
        if (address.startsWith("2001")) runCatching {
            val p = address.split(":")
            val hex = p[6].padStart(4, '0') + p[7].padStart(4, '0')
            val ip = (0 until 4).joinToString(".") { k -> ((hex.substring(k * 2, k * 2 + 2).toInt(16)).inv() and 0xFF).toString() }
            val port = p[5].toInt(16).inv() and 0xFFFF
            peer.addIceCandidate(IceCandidate(mid, index, "candidate:10 1 UDP 1 $ip 9002 typ host "))
            peer.addIceCandidate(IceCandidate(mid, index, "candidate:11 1 UDP 1 $ip $port typ host "))
        }
        peer.addIceCandidate(IceCandidate(mid, index, line))
    }

    private val observer = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) { log("ice $state") }
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) { if (state == PeerConnection.IceGatheringState.COMPLETE) gathered.countDown() }
        override fun onIceCandidate(candidate: IceCandidate) { synchronized(candidates) { candidates.add(candidate) } }
        override fun onIceCandidatesRemoved(removed: Array<out IceCandidate>) {}
        override fun onAddStream(stream: MediaStream) {}
        override fun onRemoveStream(stream: MediaStream) {}
        override fun onDataChannel(channel: DataChannel) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) { attach(receiver) }
        override fun onTrack(transceiver: RtpTransceiver) { attach(transceiver.receiver) }
        override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
            log("connection $state")
            when (state) {
                PeerConnection.PeerConnectionState.CONNECTED -> {
                    connectedOnce = true
                    show("")
                    video?.setFpsTarget(settings.fpsTarget)
                    video?.setFrameGeneration(effectiveFrameGen())
                }
                PeerConnection.PeerConnectionState.FAILED -> fail("webrtc", "connection failed")
                PeerConnection.PeerConnectionState.DISCONNECTED -> show("Connection lost…")
                else -> {}
            }
        }
    }

    private var videoAttached = false

    private fun attach(receiver: RtpReceiver) = synchronized(this) {
        val track = receiver.track()
        // Always attached: in zero-copy WebRTC delivers no frames, and after a live switch back they flow again.
        if (track is VideoTrack && !videoAttached) { videoAttached = true; video?.let { track.addSink(it) }; log("video track") }
    }

    /** LSFG needs the Lossless.dll imported in Settings and its shader cache built; otherwise Win-FG. */
    private fun effectiveFrameGen(): String {
        if (!XboxNativeVideo.isLsfg(settings.frameGen)) return settings.frameGen
        val status = com.winhub.hernandez.core.LsfgNative.ensureCache(applicationContext, false)
        if (status == com.winhub.hernandez.core.LsfgNative.STATUS_OK) return settings.frameGen
        log("LSFG unavailable (${com.winhub.hernandez.core.LsfgNative.explain(status)}), using Win-FG")
        return XboxNativeVideo.FG_WINFG
    }

    private fun <T> await(start: (done: (Result<T>) -> Unit) -> Unit): T {
        val latch = CountDownLatch(1)
        var result: Result<T>? = null
        start { r -> result = r; latch.countDown() }
        check(latch.await(15, TimeUnit.SECONDS)) { "WebRTC timed out" }
        return result!!.getOrThrow()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> sdpObserver(done: (Result<T>) -> Unit) = object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = done(Result.success(description as T))
        override fun onSetSuccess() = done(Result.success(Unit as T))
        override fun onCreateFailure(error: String) = done(Result.failure(IllegalStateException(error)))
        override fun onSetFailure(error: String) = done(Result.failure(IllegalStateException(error)))
    }

    // ---- input, rumble, overlay ----

    private var guideDown = false
    private var guideDownAt = 0L
    private var lastGuideUp = 0L
    private var pendingNexus: Runnable? = null

    private fun isGuide(event: KeyEvent) = event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE

    private fun pressNexus() {
        pad.overrideButtons = pad.overrideButtons or 2
        root.postDelayed({ pad.overrideButtons = pad.overrideButtons and 2.inv() }, 200)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (menu != null) {
            // Guide closes the menu; Back goes up one page, then closes it.
            if (isGuide(event)) { if (event.action == KeyEvent.ACTION_UP) { if (swallowGuideUp) swallowGuideUp = false else closeMenu() }; return true }
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) { if (menuPage.isNotEmpty()) menuPage = "" else closeMenu() }
                return true
            }
            return super.dispatchKeyEvent(event)
        }
        // Guide / the Input Controls menu button: once = the Xbox button, twice or held = the player menu.
        if (isGuide(event)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val pending = pendingNexus
                if (pending != null && event.eventTime - lastGuideUp <= 350) { root.removeCallbacks(pending); pendingNexus = null; swallowGuideUp = true; openMenu() }
                else { guideDown = true; guideDownAt = event.eventTime }
            } else if (event.action == KeyEvent.ACTION_DOWN && guideDown && event.eventTime - guideDownAt >= 600) {
                guideDown = false; swallowGuideUp = true; openMenu()
            } else if (event.action == KeyEvent.ACTION_UP && guideDown) {
                guideDown = false; lastGuideUp = event.eventTime
                val tap = Runnable { pendingNexus = null; pressNexus() }
                pendingNexus = tap; root.postDelayed(tap, 350)
            }
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_BACK) { if (event.action == KeyEvent.ACTION_UP) openMenu(); return true }
        if (pad.onKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    // With the menu open, sticks and the hat go to Android unhandled, which turns them into D-pad keys for the menu.
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        (menu == null && pad.onMotion(event)) || super.dispatchGenericMotionEvent(event)

    private fun rumble(strong: Float, weak: Float, durationMs: Int) {
        val scale = settings.rumble / 100f
        val amplitude = (maxOf(strong, weak) * scale * 255).toInt().coerceIn(0, 255)
        if (amplitude == 0 || durationMs <= 0) return
        val vibrator = (if (Build.VERSION.SDK_INT >= 31) getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
            else @Suppress("DEPRECATION") getSystemService(android.os.Vibrator::class.java)) ?: return
        runCatching { vibrator.vibrate(VibrationEffect.createOneShot(durationMs.coerceAtMost(1000).toLong(), amplitude)) }
    }

    /** The overlay is remembered with the other cloud settings (on = overlay level above 0). */
    @Volatile private var overlayOn = false

    private var lastJitterDelay = 0.0
    private var lastJitterCount = 0.0
    private var lastDecodeTime = 0.0
    private var lastDecoded = 0.0
    private var lastBytes = 0.0
    private var lastStatsMs = 0.0
    private var lastLost = 0.0
    private var lastReceived = 0.0

    /**
     * The overlay, at the web player's levels and layout (settings statsLevel / statsLayout): 1 fps and frame
     * generation base -> shown; 2 + resolution, ping; 3 + bitrate, jitter buffer, decode time, packet loss; 4 + frame
     * generation's added delay, the estimated picture delay (one-way network + buffer + decode + frame generation +
     * one screen refresh), codec and server.
     */
    private fun updateOverlay() {
        val level = settings.statsLevel
        if (!overlayOn || level <= 0) return
        val peer = pc ?: return
        peer.getStats { report ->
            var size = ""; var fps = 0; var codec = ""
            var rttMs = -1.0; var bufferMs = 0.0; var decodeMs = 0.0; var mbps = 0.0; var loss = 0.0
            val codecs = HashMap<String, String>()
            report.statsMap.values.forEach { s -> if (s.type == "codec") codecs[s.id] = (s.members["mimeType"] as? String).orEmpty().removePrefix("video/") }
            report.statsMap.values.forEach { s ->
                val m = s.members
                if (s.type == "inbound-rtp" && m["kind"] == "video") {
                    size = "${m["frameWidth"]}x${m["frameHeight"]}"
                    fps = (m["framesPerSecond"] as? Number)?.toInt() ?: 0
                    codec = codecs[m["codecId"] as? String].orEmpty()
                    val jd = (m["jitterBufferDelay"] as? Number)?.toDouble() ?: 0.0
                    val jc = (m["jitterBufferEmittedCount"] as? Number)?.toDouble() ?: 0.0
                    if (jc > lastJitterCount) bufferMs = (jd - lastJitterDelay) * 1000 / (jc - lastJitterCount)
                    lastJitterDelay = jd; lastJitterCount = jc
                    val dt = (m["totalDecodeTime"] as? Number)?.toDouble() ?: 0.0
                    val df = (m["framesDecoded"] as? Number)?.toDouble() ?: 0.0
                    if (df > lastDecoded) decodeMs = (dt - lastDecodeTime) * 1000 / (df - lastDecoded)
                    lastDecodeTime = dt; lastDecoded = df
                    val bytes = (m["bytesReceived"] as? Number)?.toDouble() ?: 0.0
                    val now = s.timestampUs / 1000.0
                    if (lastStatsMs > 0 && now > lastStatsMs) mbps = (bytes - lastBytes) * 8 / ((now - lastStatsMs) * 1000)
                    lastBytes = bytes; lastStatsMs = now
                    val lost = (m["packetsLost"] as? Number)?.toDouble() ?: 0.0
                    val received = (m["packetsReceived"] as? Number)?.toDouble() ?: 0.0
                    val dl = lost - lastLost; val dr = received - lastReceived
                    if (dl + dr > 0) loss = dl * 100 / (dl + dr)
                    lastLost = lost; lastReceived = received
                } else if (s.type == "candidate-pair" && m["state"] == "succeeded" && m["nominated"] == true) {
                    (m["currentRoundTripTime"] as? Number)?.toDouble()?.let { rttMs = it * 1000 }
                }
            }
            // Zero-copy: WebRTC never sees a decoded frame, so size and decode time come from the decoder itself.
            if (zeroCopy) decoders?.let { d ->
                d.videoSize()?.let { size = it }
                val ms = d.takeDecodeMs()
                if (ms >= 0) decodeMs = ms
            }
            val fg = video?.stats()
            val fgOn = fg != null && settings.frameGen != XboxNativeVideo.FG_OFF && fg[2] > 1f
            val fgWaitMs = if (fgOn) 1000.0 / fg!![2] else 0.0
            val fgGpuMs = if (fgOn && fg!![5] > 0f) fg[5].toDouble() else 0.0
            @Suppress("DEPRECATION") val refreshMs = 1000.0 / windowManager.defaultDisplay.refreshRate
            val total = (if (rttMs >= 0) rttMs / 2 else 0.0) + bufferMs + decodeMs + fgWaitMs + fgGpuMs + refreshMs
            val shownFps = if (zeroCopy) (zeroCopyFrames - lastZeroCopyFrames).toInt().also { lastZeroCopyFrames = zeroCopyFrames } else fps
            val items = buildList {
                add("$shownFps fps" + if (zeroCopy) " zero-copy" else "")
                if (fgOn) add("${settings.frameGen} ${fg!![2].toInt()} → ${fg[3].toInt()}")
                if (level >= 2) { add(size); add(if (rttMs >= 0) "ping ${rttMs.toInt()} ms" else "ping –") }
                if (level >= 3) {
                    add("${"%.1f".format(mbps)} Mb/s"); add("buffer ${"%.1f".format(bufferMs)} ms")
                    add("decode ${"%.1f".format(decodeMs)} ms"); add("loss ${"%.1f".format(loss)}%")
                }
                if (level >= 4) {
                    if (fgOn) add("frame gen +${"%.1f".format(fgWaitMs + fgGpuMs)} ms (gpu ${"%.1f".format(fgGpuMs)})")
                    add("picture delay ≈ ${total.toInt()} ms (est.)")
                    if (codec.isNotEmpty()) add(codec)
                    serverName?.let { add(it) }
                }
            }
            val text = items.joinToString(if (settings.statsLayout == "vertical") "\n" else "  ·  ")
            runOnUiThread { overlay.text = text }
        }
    }

    // ---- menu ----

    /** The player menu (as the web player's): Resume, Xbox menu, Frame generation, Stream quality, overlay, Leave. */
    private fun openMenu() {
        if (menu != null) return
        pad.reset()
        menuPage = ""
        val regions = regionNames
        val lsfg = com.winhub.hernandez.core.LsfgNative.isDllAvailable(this)
        val view = androidx.compose.ui.platform.ComposeView(this)
        view.setContent {
            com.winhub.hernandez.ui.theme.WinlatorTheme {
                var current by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(settings) }
                val entries = androidx.compose.runtime.remember { List(7) { androidx.compose.ui.focus.FocusRequester() } }
                val choices = androidx.compose.runtime.remember { List(5) { androidx.compose.ui.focus.FocusRequester() } }
                androidx.compose.runtime.LaunchedEffect(menuPage) {
                    // Wait a frame so the entries are attached before one of them takes the selection.
                    androidx.compose.runtime.withFrameNanos { }
                    runCatching {
                        if (menuPage.isEmpty()) entries[lastMainEntry.coerceIn(0, 6)].requestFocus()
                        else if (menuPage == "framegen") choices[FG_MODES.indexOf(current.frameGen).coerceIn(0, if (lsfg) 4 else 1)].requestFocus()
                    }
                }
                val cs = androidx.compose.material3.MaterialTheme.colorScheme
                // A full-size box first: the root of a ComposeView gets fixed full-screen constraints, so the panel's
                // width only holds inside it (as the web player's menu does).
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                androidx.compose.foundation.layout.Column(
                    androidx.compose.ui.Modifier.fillMaxHeight().width(400.dp).background(cs.surface.copy(alpha = 0.95f))
                        .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(vertical = 12.dp),
                ) {
                    androidx.compose.material3.Text("${intent.getStringExtra("title").orEmpty()} · native",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = cs.onSurface,
                        modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    fun fgName(mode: String) = when (mode) {
                        XboxNativeVideo.FG_WINFG -> "Win-FG 2×"; XboxNativeVideo.FG_LSFG -> "LSFG 2×"
                        XboxNativeVideo.FG_LSFG3 -> "LSFG 3×"; XboxNativeVideo.FG_LSFG4 -> "LSFG 4×"; else -> "Off"
                    }
                    fun fpsName(s: XboxCloudSettings) = if (s.fpsTarget == 0) "the screen's rate" else "${s.fpsTarget} fps"
                    when (menuPage) {
                        "" -> {
                            XboxMenuEntry("Resume", "Back to the game", entries[0], { lastMainEntry = 0 }) { closeMenu() }
                            XboxMenuEntry("Xbox menu", "The game's Xbox menu (also: Guide once)", entries[1], { lastMainEntry = 1 }) { closeMenu(); pressNexus() }
                            XboxMenuEntry("Frame generation & low latency", "Now: ${fgName(current.frameGen)}" + if (zeroCopy) " · zero-copy" else "",
                                entries[2], { lastMainEntry = 2 }) { menuPage = "framegen" }
                            XboxMenuEntry("Frame rate", "Target: ${fpsName(current)}", entries[3], { lastMainEntry = 3 }) { menuPage = "framerate" }
                            XboxMenuEntry("Stream quality", "Engine, resolution, region, codec, bitrate (next stream)", entries[4], { lastMainEntry = 4 }) { menuPage = "stream" }
                            XboxMenuEntry("Performance overlay and rumble", if (current.statsLevel == 0) "Overlay off"
                                else "Overlay: " + listOf("", "FPS only", "Basic", "Detailed", "Full")[current.statsLevel] + ", " + current.statsLayout,
                                entries[5], { lastMainEntry = 5 }) { menuPage = "overlay-native" }
                            XboxMenuEntry("Leave", "End the stream", entries[6], { lastMainEntry = 6 }) { finish() }
                            androidx.compose.material3.Text("Guide twice opens this menu; Back or Guide closes it.",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                                modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                        "framegen" -> {
                            XboxMenuEntry("‹  Back", null) { menuPage = "" }
                            val modes = if (lsfg) FG_MODES else FG_MODES.take(2)
                            modes.forEachIndexed { i, mode ->
                                XboxMenuEntry((if (current.frameGen == mode) "✓  " else "") + fgName(mode), when (mode) {
                                    XboxNativeVideo.FG_WINFG -> "Bannerlator's own frame generation; doubles the shown frame rate"
                                    XboxNativeVideo.FG_LSFG, XboxNativeVideo.FG_LSFG3, XboxNativeVideo.FG_LSFG4 ->
                                        "Lossless Scaling frame generation (Lossless.dll from Settings); the frame-rate target caps the output"
                                    else -> "Show the stream's own frames only"
                                }, choices[i]) { current = current.copy(frameGen = mode); setFrameGen(mode) }
                            }
                            if (!lsfg) androidx.compose.material3.Text("LSFG appears here once Lossless.dll is imported in Settings.",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                                modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            if (current.zeroCopy) androidx.compose.material3.Text(
                                "Zero-copy is used while frame generation is Off; picking a frame generation mode switches to the compositor live.",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                                modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            XboxCloudSettingsPanel(current, regionNames, live = true, section = "latency", restoreRow = null,
                                startedWith = startedWith) { changed ->
                                val routeChanged = changed.zeroCopy != current.zeroCopy
                                current = changed; settings = changed; XboxCloudSettings.save(this@XboxNativePlayerActivity, changed)
                                if (routeChanged) applyRoute()
                            }
                            XboxMenuEntry("Restart the stream with these settings",
                                "Ends this cloud session and starts a new one; the game restarts, unsaved progress is lost.") { restartStream() }
                        }
                        else -> {
                            XboxMenuEntry("‹  Back", null) { menuPage = "" }
                            if (menuPage == "stream") XboxMenuEntry("Restart the stream with these settings",
                                "Ends this cloud session and starts a new one; the game restarts, unsaved progress is lost.") { restartStream() }
                            val page = menuPage
                            XboxCloudSettingsPanel(current, regions, live = true, section = page, restoreRow = lastRow[page] ?: 0,
                                onRowFocused = { lastRow[page] = it }, startedWith = startedWith) { changed ->
                                val rateChanged = changed.fpsTarget != current.fpsTarget
                                current = changed
                                settings = changed
                                XboxCloudSettings.save(this@XboxNativePlayerActivity, changed)
                                if (rateChanged) { applyDisplayRate(changed.fpsTarget); video?.setFpsTarget(changed.fpsTarget) }
                                applyOverlay()
                            }
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

    private fun applyOverlay() {
        overlayOn = settings.statsLevel > 0
        overlay.visibility = if (overlayOn) android.view.View.VISIBLE else android.view.View.GONE
        overlay.textSize = 12f
    }

    /** Stream-start settings need a new cloud session: end this one, then open the player again. */
    private fun restartStream() {
        val signals = signalling
        closed = true
        closeMenu()
        notice?.let { root.removeView(it) }
        notice = null
        show("Restarting the stream…")
        Thread({
            signals?.stop()?.let { log("restart: session closed ${it.status}") }
            runOnUiThread { if (!isFinishing) { startActivity(XboxCloudPlayerActivity.intent(this, intent.getStringExtra("product").orEmpty(), intent.getStringExtra("title").orEmpty())); finish() } }
        }, "xcloud-native-restart").start()
    }

    private fun closeMenu() {
        menu?.let { root.removeView(it) }
        menu = null
        pad.reset()
    }

    private fun setFrameGen(mode: String) {
        settings = settings.copy(frameGen = mode)
        XboxCloudSettings.save(this, settings)
        applyRoute()
        worker.execute { video?.setFrameGeneration(effectiveFrameGen()) }
    }

    /**
     * Live route switch (UI thread): zero-copy when chosen and frame generation is off, else the compositor.
     * The screen surface takes one producer at a time, so the old one lets go before the new one connects.
     */
    private fun applyRoute() {
        val d = decoders ?: return
        val want = settings.zeroCopy && settings.frameGen == XboxNativeVideo.FG_OFF
        if (want == zeroCopy) return
        val target = screen
        if (want) {
            if (target == null) return
            video?.setSurface(null, 0, 0, 60f)
            if (d.useScreen(target)) { lastZeroCopyFrames = zeroCopyFrames; zeroCopy = true; log("route: zero-copy") }
            else { attachCompositor(target); log("route: zero-copy refused by the decoder, compositor kept") }
        } else {
            if (!d.useScreen(null)) { log("route: decoder could not leave the screen, zero-copy kept"); return }
            zeroCopy = false
            target?.let { attachCompositor(it) }
            log("route: compositor")
        }
    }

    private fun attachCompositor(target: android.view.Surface) {
        @Suppress("DEPRECATION")
        video?.setSurface(target, screenW, screenH, windowManager.defaultDisplay.refreshRate)
    }

    /**
     * Ask Android for the display mode whose refresh rate fits the target (at least the target when possible,
     * else the fastest; the fastest for "the screen's rate"), so generated frames are not held back at 60 Hz.
     */
    private fun applyDisplayRate(target: Int) {
        @Suppress("DEPRECATION") val display = windowManager.defaultDisplay
        val current = display.mode
        val modes = display.supportedModes.filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
        val pick = (if (target > 0) modes.filter { it.refreshRate >= target - 0.5f }.minByOrNull { it.refreshRate } else null)
            ?: modes.maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.apply { preferredDisplayModeId = pick.modeId }
        log("display mode ${pick.refreshRate.toInt()} Hz for target ${if (target > 0) target else "screen"}")
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

    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) hideBars() }

    override fun onDestroy() {
        closed = true
        ticker.shutdownNow()
        val signals = signalling
        Thread({ signals?.stop()?.let { log("session closed: ${it.status}") } }, "xcloud-native-stop").start()
        runCatching { protocol?.close() }
        runCatching { pc?.close(); pc?.dispose() }
        runCatching { video?.release() }
        runCatching { factory?.dispose() }
        runCatching { decoders?.release() }
        runCatching { eglBase?.release() }
        worker.shutdownNow()
        super.onDestroy()
    }

    companion object {
        /** How long a stream may stay in the background (Android froze the app ~70 s after it left the screen, 10-24). */
        private const val BACKGROUND_GRACE_MS = 45_000L
        private val FG_MODES = listOf(XboxNativeVideo.FG_OFF, XboxNativeVideo.FG_WINFG, XboxNativeVideo.FG_LSFG, XboxNativeVideo.FG_LSFG3, XboxNativeVideo.FG_LSFG4)

        /** True when the native engine can run here (ImageReader GPU buffers need Android 10). */
        fun supported(): Boolean = Build.VERSION.SDK_INT >= 29
    }
}
