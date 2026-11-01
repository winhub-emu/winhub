package com.winhub.hernandez.store.xbox

import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.PeerConnection
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * The xCloud data-channel protocol for the native player, ported from Greenlight's player (UnknownSKL, MIT;
 * tools/xcloud-player/greenlight/lib/channel): chat / control / input / message channels, the message
 * handshake, authorization and gamepad announcements, and the binary input reports. The controller state
 * comes from Android input events ([pad]); rumble reports come back through [onRumble].
 */
internal class XboxNativeProtocol(
    pc: PeerConnection,
    private val pad: XboxNativePad,
    private val log: (String) -> Unit,
    private val onRumble: (strong: Float, weak: Float, durationMs: Int) -> Unit,
    private val onServerDisconnect: () -> Unit,
) {
    private val chat = pc.createDataChannel("chat", init("chatV1"))
    private val control = pc.createDataChannel("control", init("controlV1"))
    private val input = pc.createDataChannel("input", init("1.0"))
    private val message = pc.createDataChannel("message", init("messageV1"))
    private val timer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "xcloud-input") }
    private var loop: ScheduledFuture<*>? = null
    private var sequence = 0
    private var lastSent: XboxNativePad.State? = null
    private var lastSentAt = 0L
    @Volatile var handshakeDone = false
        private set

    init {
        message.registerObserver(ChannelObserver(message, ::onMessageOpen, ::onMessageData))
        control.registerObserver(ChannelObserver(control, {}, {}))
        input.registerObserver(ChannelObserver(input, {}, ::onInputData))
        chat.registerObserver(ChannelObserver(chat, {}, {}))
    }

    private fun init(protocol: String) = DataChannel.Init().apply { ordered = true; this.protocol = protocol }

    private inner class ChannelObserver(
        private val channel: DataChannel,
        private val onOpen: () -> Unit,
        private val onData: (DataChannel.Buffer) -> Unit,
    ) : DataChannel.Observer {
        private var opened = false
        override fun onBufferedAmountChange(previous: Long) {}
        override fun onStateChange() {
            if (!opened && channel.state() == DataChannel.State.OPEN) { opened = true; runCatching(onOpen).onFailure { log("channel open failed: ${it.message}") } }
        }
        override fun onMessage(buffer: DataChannel.Buffer) { runCatching { onData(buffer) }.onFailure { log("channel message failed: ${it.message}") } }
    }

    private fun send(channel: DataChannel, text: String) {
        if (channel.state() != DataChannel.State.OPEN) return
        channel.send(DataChannel.Buffer(ByteBuffer.wrap(text.toByteArray(StandardCharsets.UTF_8)), false))
    }

    private fun send(channel: DataChannel, bytes: ByteBuffer) {
        if (channel.state() != DataChannel.State.OPEN) return
        channel.send(DataChannel.Buffer(bytes, true))
    }

    private fun onMessageOpen() {
        log("message channel open, handshake")
        send(message, JSONObject().put("type", "Handshake").put("version", "messageV1")
            .put("id", "be0bfc6d-1e83-4c8a-90ed-fa8601c5a179").put("cv", "0").toString())
    }

    private fun onMessageData(buffer: DataChannel.Buffer) {
        val bytes = ByteArray(buffer.data.remaining()).also { buffer.data.get(it) }
        val data = JSONObject(String(bytes, StandardCharsets.UTF_8))
        when (data.optString("type")) {
            "HandshakeAck" -> {
                handshakeDone = true
                log("handshake done")
                // Greenlight's order: authorization (announces controller 0 and removes it), input start, config.
                send(control, JSONObject().put("message", "authorizationRequest").put("accessKey", "4BDB3609-C1F1-4195-9B37-FEFF45DA8B8E").toString())
                gamepadChanged(0, true); gamepadChanged(0, false)
                startInput()
                sendConfig()
                // Our controller, after the authorization's add/remove.
                gamepadChanged(0, true)
                send(control, JSONObject().put("message", "videoKeyframeRequested").put("ifrRequested", true).toString())
            }
            "TransactionStart", "Message" -> handleTransaction(data)
            else -> {}
        }
    }

    private fun handleTransaction(data: JSONObject) {
        when (data.optString("target")) {
            "/streaming/sessionLifetimeManagement/serverInitiatedDisconnect" -> {
                complete(data.optString("id"), "")
                onServerDisconnect()
            }
            // The game's own dialogs: accept (Greenlight shows a confirm; the native player has no page).
            "/streaming/systemUi/messages/ShowMessageDialog" -> complete(data.optString("id"), JSONObject().put("Result", 0))
            else -> {}
        }
    }

    private fun complete(id: String, content: Any) {
        send(message, JSONObject().put("type", "TransactionComplete").put("content", content.toString().let { if (content is String) JSONObject.quote(it) else it })
            .put("id", id).put("cv", "").toString())
    }

    private fun gamepadChanged(index: Int, added: Boolean) {
        send(control, JSONObject().put("message", "gamepadChanged").put("gamepadIndex", index).put("wasAdded", added).toString())
    }

    private fun sendConfig() {
        fun msg(path: String, content: JSONObject) = JSONObject().put("type", "Message").put("content", content.toString())
            .put("id", UUID.randomUUID().toString()).put("target", path).put("cv", "").toString()
        send(message, msg("/streaming/systemUi/configuration", JSONObject().put("version", org.json.JSONArray(listOf(0, 2, 0))).put("systemUis", org.json.JSONArray())))
        send(message, msg("/streaming/properties/clientappinstallidchanged", JSONObject().put("clientAppInstallId", "c97d7ee0-73b2-4239-bf1d-9d805a338429")))
        send(message, msg("/streaming/characteristics/orientationchanged", JSONObject().put("orientation", 0)))
        send(message, msg("/streaming/characteristics/touchinputenabledchanged", JSONObject().put("touchInputEnabled", false)))
        send(message, msg("/streaming/characteristics/clientdevicecapabilities", JSONObject()))
        send(message, msg("/streaming/characteristics/dimensionschanged", JSONObject().put("horizontal", 1920).put("vertical", 1080)
            .put("preferredWidth", 1920).put("preferredHeight", 1080).put("safeAreaLeft", 0).put("safeAreaTop", 0)
            .put("safeAreaRight", 1920).put("safeAreaBottom", 1080).put("supportsCustomResolution", true)))
    }

    private fun header(type: Int, size: Int): ByteBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.LITTLE_ENDIAN).apply {
        putShort(type.toShort()); putInt(sequence++); putDouble(System.nanoTime() / 1e6)
    }

    private fun startInput() {
        // Client metadata: max touch points.
        send(input, header(8, 15).apply { put(1.toByte()); flip() })
        loop = timer.scheduleWithFixedDelay({ runCatching { tick() } }, 16, 16, TimeUnit.MILLISECONDS)
    }

    /** Instant controller input: send a report now if the state changed (the 16 ms loop stays as the heartbeat). */
    fun inputChanged() {
        // At most one extra report every 4 ms (stick motion arrives far faster than that).
        if (handshakeDone && loop != null && System.nanoTime() - lastSentAt > 4_000_000L) timer.execute { runCatching { tick() } }
    }

    /** Greenlight's queue: a gamepad frame when the state changed (sticks past a small threshold) or every 33 ms. */
    @Synchronized
    private fun tick() {
        val state = pad.snapshot()
        val now = System.nanoTime()
        val changed = lastSent?.let { state.differsFrom(it) } ?: true
        if (!changed && now - lastSentAt < 33_000_000L) return
        lastSent = state; lastSentAt = now
        val packet = header(2, 14 + 1 + 23).apply {
            put(1.toByte())                    // one frame
            put(0.toByte())                    // gamepad index
            putShort(state.buttons.toShort())
            putShort(axis(state.lx)); putShort(axis(-state.ly)); putShort(axis(state.rx)); putShort(axis(-state.ry))
            putShort(trigger(state.lt)); putShort(trigger(state.rt))
            putInt(1)                          // physical physicality
            order(ByteOrder.BIG_ENDIAN); putInt(1); order(ByteOrder.LITTLE_ENDIAN)   // virtual physicality (Greenlight writes it big-endian)
            flip()
        }
        send(input, packet)
    }

    private fun axis(value: Float): Short = (value * 32767).toInt().coerceIn(-32767, 32767).toShort()
    private fun trigger(value: Float): Short = if (value <= 0f) 0 else if (value >= 1f) 65535.toShort() else (value * 65535).toInt().toShort()

    private fun onInputData(buffer: DataChannel.Buffer) {
        val data = buffer.data.order(ByteOrder.LITTLE_ENDIAN)
        if (data.remaining() < 13 || (data.get(0).toInt() and 0xFF) != 128) return   // vibration report
        val strong = (data.get(4).toInt() and 0xFF) / 100f
        val weak = (data.get(5).toInt() and 0xFF) / 100f
        val duration = data.getShort(8).toInt() and 0xFFFF
        onRumble(strong, weak, duration)
    }

    fun close() {
        loop?.cancel(false)
        timer.shutdownNow()
        listOf(chat, control, input, message).forEach { runCatching { it.unregisterObserver(); it.close(); it.dispose() } }
    }
}
