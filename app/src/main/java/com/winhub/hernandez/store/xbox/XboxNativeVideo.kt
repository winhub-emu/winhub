package com.winhub.hernandez.store.xbox

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import org.webrtc.EglBase
import org.webrtc.EglRenderer
import org.webrtc.GlRectDrawer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import java.util.ArrayDeque

/**
 * The native player's picture: WebRTC's hardware-decoded frames are drawn (GL, WebRTC's EglRenderer) into an
 * ImageReader whose images are GPU-shareable HardwareBuffers, and each image is handed to Bannerlator's Vulkan
 * compositor (cpp/winlator/stream_jni.cpp) as the one full-screen window, where native frame generation
 * (Win-FG, or LSFG with the user's Lossless.dll) multiplies it. Android 10+ (ImageReader usage flags).
 */
internal class XboxNativeVideo(private val context: Context, eglBase: EglBase, private val log: (String) -> Unit) : VideoSink {
    companion object {
        init { System.loadLibrary("vulkan_renderer") }
        const val FG_OFF = "off"
        const val FG_WINFG = "winfg"
        const val FG_LSFG = "lsfg"
        const val FG_LSFG3 = "lsfg3"
        const val FG_LSFG4 = "lsfg4"
        fun isLsfg(mode: String) = mode == FG_LSFG || mode == FG_LSFG3 || mode == FG_LSFG4
        fun multiplier(mode: String) = when (mode) { FG_LSFG3 -> 3; FG_LSFG4 -> 4; FG_OFF -> 0; else -> 2 }
        private const val ENGINE_LSFG = 0
        private const val ENGINE_WINFG = 1
    }

    private external fun nativeCreate(surface: Surface, videoW: Int, videoH: Int): Long
    private external fun nativeResize(handle: Long, w: Int, h: Int)
    private external fun nativeSetTransform(handle: Long, ox: Float, oy: Float, sx: Float, sy: Float)
    private external fun nativeFrame(handle: Long, buffer: HardwareBuffer, w: Int, h: Int)
    private external fun nativeFrameGen(handle: Long, engine: Int, multiplier: Int, flowScale: Float, refreshHz: Float,
                                        model: Int, preset: Int, lsfgCache: String?)
    private external fun nativeSetSwapRB(handle: Long, swap: Boolean)
    private external fun nativeSetPresentMode(handle: Long, mode: Int)
    private external fun nativeFrameGenProblem(handle: Long): Int
    private external fun nativeStats(handle: Long): FloatArray
    private external fun nativeDestroy(handle: Long)

    private val lock = Any()
    private val egl = EglRenderer("xcloud-native").apply { init(eglBase.eglBaseContext, EglBase.CONFIG_RGBA, GlRectDrawer()) }
    private val readerThread = HandlerThread("xcloud-frames").apply { start() }
    private val readerHandler = Handler(readerThread.looper)
    private var reader: ImageReader? = null
    private var readerW = 0
    private var readerH = 0
    /** Images still being composited: the oldest is returned to the reader once two newer ones arrived. */
    private val held = ArrayDeque<Image>()
    private var handle = 0L
    private var surface: Surface? = null
    private var surfaceW = 0
    private var surfaceH = 0
    private var videoW = 0
    private var videoH = 0
    @Volatile private var frameGen = FG_OFF
    @Volatile private var refreshHz = 60f
    /** The frame-rate target (0 = the screen's rate): the frame-gen pacer's cap, or a limit on stream frames without it. */
    @Volatile private var fpsTarget = 0
    private var lastSubmitNs = 0L
    @Volatile var frames = 0L
        private set

    fun setSurface(target: Surface?, width: Int, height: Int, displayRefreshHz: Float) = synchronized(lock) {
        refreshHz = displayRefreshHz
        if (target == null) { destroyRenderer(); surface = null; return@synchronized }
        val changed = target !== surface
        surface = target; surfaceW = width; surfaceH = height
        if (changed) destroyRenderer()
        if (handle != 0L) { nativeResize(handle, width, height); applyTransform() }
    }

    /** Frame-rate target, 0 = the screen's rate. Live. */
    fun setFpsTarget(fps: Int) {
        fpsTarget = fps
        synchronized(lock) { if (handle != 0L) applyFrameGen() }
    }

    /** off, winfg (2x) or lsfg / lsfg3 / lsfg4 (needs the imported Lossless.dll). Live. */
    fun setFrameGeneration(mode: String) {
        frameGen = mode
        synchronized(lock) { if (handle != 0L) applyFrameGen() }
    }

    override fun onFrame(frame: VideoFrame) {
        val w = frame.rotatedWidth; val h = frame.rotatedHeight
        synchronized(lock) { if (w != readerW || h != readerH) startReader(w, h) }
        egl.onFrame(frame)
    }

    private fun startReader(w: Int, h: Int) {
        val old = reader
        if (old != null) egl.releaseEglSurface { }
        held.forEach { runCatching { it.close() } }; held.clear()
        old?.close()
        readerW = w; readerH = h
        val next = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 4,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)
        next.setOnImageAvailableListener({ r -> onImage(r) }, readerHandler)
        reader = next
        egl.createEglSurface(next.surface)
        log("native video ${w}x$h")
    }

    private fun onImage(r: ImageReader) {
        val image = runCatching { r.acquireLatestImage() }.getOrNull() ?: return
        synchronized(lock) {
            if (r !== reader) { image.close(); return }
            if (image.width != videoW || image.height != videoH || handle == 0L) startRenderer(image.width, image.height)
            val buffer = image.hardwareBuffer
            // Without frame generation a target below the stream's rate drops stream frames evenly.
            val now = System.nanoTime()
            val skip = frameGen == FG_OFF && fpsTarget in 1 until 60 && now - lastSubmitNs < 1_000_000_000L / fpsTarget - 2_000_000L
            if (handle != 0L && buffer != null && !skip) { nativeFrame(handle, buffer, image.width, image.height); frames++; lastSubmitNs = now }
            buffer?.close()
            held.addLast(image)
            while (held.size > 2) held.removeFirst().close()
        }
    }

    private fun startRenderer(w: Int, h: Int) {
        destroyRenderer()
        val target = surface ?: return
        handle = nativeCreate(target, w, h)
        if (handle == 0L) { log("native renderer could not start"); return }
        videoW = w; videoH = h
        nativeSetSwapRB(handle, true)
        nativeResize(handle, surfaceW, surfaceH)
        applyTransform()
        applyFrameGen()
        log("native renderer ${w}x$h on ${surfaceW}x$surfaceH")
    }

    /** Fit the video into the surface, letterboxed (the compositor normalises by the video size). */
    private fun applyTransform() {
        if (handle == 0L || videoW == 0 || surfaceW == 0) return
        val scale = minOf(surfaceW.toFloat() / videoW, surfaceH.toFloat() / videoH)
        val viewW = videoW * scale; val viewH = videoH * scale
        val offX = (surfaceW - viewW) / 2f; val offY = (surfaceH - viewH) / 2f
        nativeSetTransform(handle, videoW * offX / surfaceW, videoH * offY / surfaceH, viewW / surfaceW, viewH / surfaceH)
    }

    private fun applyFrameGen() {
        // The pacer caps generated output at this rate: the user's target, else the screen's.
        val cap = if (fpsTarget > 0) fpsTarget.toFloat() else refreshHz
        when {
            frameGen == FG_WINFG -> nativeFrameGen(handle, ENGINE_WINFG, 2, com.winhub.hernandez.container.Container.FRAMEGEN_DEFAULT_FLOW_SCALE,
                cap, com.winhub.hernandez.container.Container.FRAMEGEN_DEFAULT_MODEL, 2, null)
            isLsfg(frameGen) -> {
                val cache = com.winhub.hernandez.core.LsfgNative.cacheFile(context)
                nativeFrameGen(handle, ENGINE_LSFG, multiplier(frameGen), com.winhub.hernandez.container.Container.LSFG_DEFAULT_FLOW_SCALE, cap, 0, 0, cache.absolutePath)
            }
            else -> nativeFrameGen(handle, ENGINE_WINFG, 0, com.winhub.hernandez.container.Container.FRAMEGEN_DEFAULT_FLOW_SCALE, cap, 0, 2, null)
        }
        // FIFO while multiplying (the X11 rule); the compositor's own choice otherwise.
        if (frameGen != FG_OFF) nativeSetPresentMode(handle, 2)
        log("native frame generation: $frameGen, target ${if (fpsTarget > 0) fpsTarget else "screen"} fps")
    }

    /** {accepted, planned, sourceFps, presentedFps, thermal, chainMsPerGen}, or null without a renderer. */
    fun stats(): FloatArray? = synchronized(lock) { if (handle != 0L) nativeStats(handle) else null }

    fun frameGenProblem(): Int = synchronized(lock) { if (handle != 0L) nativeFrameGenProblem(handle) else -1 }

    private fun destroyRenderer() {
        if (handle != 0L) { nativeDestroy(handle); handle = 0L }
        videoW = 0; videoH = 0
    }

    fun release() {
        egl.release()
        synchronized(lock) {
            held.forEach { runCatching { it.close() } }; held.clear()
            reader?.close(); reader = null
            destroyRenderer()
        }
        readerThread.quitSafely()
    }
}
