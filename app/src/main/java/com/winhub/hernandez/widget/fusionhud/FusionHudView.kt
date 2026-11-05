package com.winhub.hernandez.widget.fusionhud

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.format.DateFormat
import android.view.MotionEvent
import android.view.View
import com.winhub.hernandez.container.Container
import com.winhub.hernandez.core.KeyValueSet
import com.winhub.hernandez.ui.theme.AppThemeState
import com.winhub.hernandez.widget.FpsCounter
import com.winhub.hernandez.widget.HudLockController
import com.winhub.hernandez.widget.HudMetrics
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.function.BiConsumer
import java.util.function.Consumer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fusion HUD, the in-game performance overlay. One look modelled on MangoHud's Steam Deck overlay: a dark
 * panel, a big FPS number that turns amber and red as it drops, a coloured label per metric and white
 * values. Four detail levels (see [FusionSize]): FPS, Graph, Stats and Everything. It is driven by the
 * shared [FpsCounter] (including the percentile lows) and one cached [HudMetrics.Snapshot]. Pure Canvas.
 *
 * The view refreshes itself on its own ~1 s Main-dispatcher coroutine, never on a present tick,
 * collecting the snapshot on Dispatchers.IO so no sysfs read touches the UI thread and nothing runs on
 * the epoll or render path. Only [FpsCounter.tick] runs on the epoll thread (elsewhere).
 *
 * Gestures go through [HudLockController]: long-press toggles the position lock (with a lock/unlock
 * badge fade), a tap steps the level (1 to 4, then back to 1), and a drag repositions. Tap and drag are
 * frozen while locked.
 */
class FusionHudView(
    context: Context,
    private val fpsProvider: () -> Float,
) : View(context) {

    /** Java-friendly entry point, symmetric with the other overlays. */
    constructor(context: Context) : this(context, { 0f })

    private var fpsCounter: FpsCounter? = null
    fun setFpsCounter(counter: FpsCounter?) { fpsCounter = counter }

    // Frames per second actually reaching the panel. FpsCounter is ticked once
    // per GUEST frame, so it counts what the game draws and cannot see frames
    // added after it: with native LSFG generating three for every one, this HUD
    // showed 30 while the display was showing 118. 0 = nothing is adding
    // frames, and the readout is exactly what it always was.
    private var presentedFps = 0f
    fun setPresentedFps(fps: Float) { presentedFps = fps }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var updateJob: Job? = null
    private val metrics = HudMetrics(context)

    private val density = resources.displayMetrics.density

    // ---- Config -----------------------------------------------------------
    private var size = FusionSize.STATS
    private var scale = Container.DEFAULT_HUD_SCALE / 100f
    private var bgOpacity = 0.62f
    private var outlineIntensity = 0.4f
    private var outlineFollowAccent = false
    private var colorIntensity = 1f
    private var tempDisplay = HudMetrics.TempDisplay.from(null)

    private var showFPS = true
    private var showEngine = true
    private var showGpuModel = true
    private var showCPU = true
    private var showGPU = true
    private var showCpuTemp = true
    private var showGpuTemp = false
    private var showVram = true
    private var showRAM = true
    private var showBattery = true
    private var showPower = true
    private var showBatteryTemp = false
    private var showGraph = false
    private var showLow001 = true
    private var fpsDecimal = true
    private var showClockTime = true   // subtle corner/footer clock, available in EVERY size
    // Mega-only rows
    private var showPerCore = true
    private var showSwap = true
    private var showNet = true
    private var showResolution = true
    private var showProton = true
    private var showWrapper = true
    private var showDxVer = true
    private var showSession = true

    private var engineLabel = ""
    private var displayServer = ""     // "X11" / "Wayland": which display server the game runs on
    private var hdrState = FusionHdr.NONE  // Wayland HDR, on its own line under latency · display server
    private var gpuModel = ""
    // The GPU name a Wayland session reports to games instead of the real adapter ("" = none). It has
    // to travel separately from gpuModel: Wayland hands the spoof to DXVK and leaves the Vulkan device
    // alone, so everything the host reads back is still the real chip. On X11 the driver wrapper renames
    // the device itself, so there gpuModel already IS the spoofed name and this stays empty.
    private var gpuSpoofName = ""
    // Stack-layer version strings (Mega bottom band + DX version on the engine row); fed by the host.
    private var wineVersion = ""       // "Proton 10.0-4"
    private var graphicsWrapper = ""   // graphics-driver wrapper package, e.g. "GameNative", "bcn_layer 20260719"
    private var dxvkVersion = ""       // DXVK version, appended to a DXVK engine row
    private var vkd3dVersion = ""      // VKD3D version, appended to a VKD3D engine row

    // ---- Live data (updated on the refresh coroutine, read on draw) --------
    private var snap: HudMetrics.Snapshot? = null
    private var fpsNow = 0f
    private var fpsAvg = 0f
    private var lows: FpsCounter.FrametimeLows = FpsCounter.FrametimeLows.EMPTY
    private val graphSamples = ArrayDeque<Float>()

    // ---- Colors (mockup) --------------------------------------------------
    private val colGpu = 0xFF2E9762.toInt()
    private val colCpu = 0xFF2E97CB.toInt()
    private val colVram = 0xFFAD64C1.toInt()
    private val colRam = 0xFFC26693.toInt()
    private val colBat = 0xFFF5C542.toInt()
    private val colFps = 0xFFEB5B5B.toInt()      // engine / API tag
    private val colGraph = 0xFF00E676.toInt()
    private val colValue = 0xFFF2F5F9.toInt()
    private val colDim = 0xFF9AA4B2.toInt()
    private val colLo = 0xFFE4E8EE.toInt()
    private val colDisp = 0xFF4DD0E1.toInt()   // display server (X11 / Wayland)
    private val colHdr = 0xFFFFD54F.toInt()    // HDR on screen

    // ---- Paints -----------------------------------------------------------
    private val boldFace: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    private val regularFace: Typeface = Typeface.MONOSPACE
    private val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = boldFace }
    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = boldFace }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val graphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        color = colGraph
    }

    // ---- Built geometry ---------------------------------------------------
    private class Glyph(val x: Float, val baseline: Float, val text: String, val color: Int, val sizePx: Float, val bold: Boolean = true)
    /** A run of text; [bold] is false for the dim units, like the mock-up. */
    private class Span(val text: String, val color: Int, val sizePx: Float, val bold: Boolean = true)
    /** A group placed against the panel's right edge once its width is known. */
    private class RightRun(val baseline: Float, val spans: List<Span>)
    private val pendingRight = ArrayList<RightRun>()
    /**
     * A "label + value-run" row for the aligned grid sizes (Full / Mega). When [inline] is true the row
     * opts OUT of the shared label column — its value run is placed directly after the label — so a long
     * label (the graphics-wrapper on the FPS row) can't widen the column and shove every value right.
     */
    private class HudRow(val label: Span, val vals: List<Span>, val inline: Boolean = false, val right: List<Span> = emptyList())
    private val glyphs = ArrayList<Glyph>()
    /** Thin divider lines between blocks (their y; drawn across the panel). */
    private val sepYs = ArrayList<Float>()
    /** A per-core load bar: its track and how full it is. */
    private class Bar(val rect: RectF, val frac: Float)
    private val bars = ArrayList<Bar>()
    private var padPx = 0f
    private var graphRect: RectF? = null
    private var contentW = 0f
    private var contentH = 0f

    // ---- Listeners --------------------------------------------------------
    private var onMovedListener: BiConsumer<Float, Float>? = null
    private var onSizeCycledListener: Consumer<String>? = null
    private var onLockChangedListener: Consumer<Boolean>? = null
    fun setOnMovedListener(l: BiConsumer<Float, Float>?) { onMovedListener = l }
    fun setOnSizeCycledListener(l: Consumer<String>?) { onSizeCycledListener = l }
    fun setOnLockChangedListener(l: Consumer<Boolean>?) { onLockChangedListener = l }

    private val lockController = HudLockController(context, this, object : HudLockController.Callbacks {
        override fun onTap() { cycleSize() }
        override fun onMoved(x: Float, y: Float) { onMovedListener?.accept(x, y) }
        override fun onLockChanged(locked: Boolean) { onLockChangedListener?.accept(locked) }
    })

    // ---- Public surface (symmetric with the other overlays) ---------------
    fun setEngineLabel(s: String?) { engineLabel = s ?: ""; post { rebuildAndInvalidate() } }
    fun setGpuModel(s: String?) { gpuModel = s ?: ""; post { rebuildAndInvalidate() } }
    /** This session's Wayland GPU-name spoof (null / empty = none, the default): the GPU row names it
     *  in place of the real model, marked so it can't be read as the chip that is really rendering. */
    fun setGpuSpoofName(s: String?) { gpuSpoofName = s ?: ""; post { rebuildAndInvalidate() } }
    fun setDisplayServer(s: String?) { displayServer = s ?: ""; post { rebuildAndInvalidate() } }
    /** [FusionHdr] code; [FusionHdr.NONE] (the default) draws no HDR line anywhere. Any thread. */
    fun setHdrState(state: Int) {
        val v = if (state in FusionHdr.ON..FusionHdr.NOT_ON_THIS_SCREEN) state else FusionHdr.NONE
        post { if (v != hdrState) { hdrState = v; rebuildAndInvalidate() } }
    }
    fun setWineVersion(s: String?) { wineVersion = s ?: ""; post { rebuildAndInvalidate() } }
    fun setGraphicsWrapper(s: String?) { graphicsWrapper = s ?: ""; post { rebuildAndInvalidate() } }
    fun setDxWrapper(dxvk: String?, vkd3d: String?) {
        dxvkVersion = dxvk ?: ""; vkd3dVersion = vkd3d ?: ""; post { rebuildAndInvalidate() }
    }

    /** Fusion has no orientation (tap cycles size instead); kept for a symmetric host surface. */
    fun setVertical(vertical: Boolean) { /* no-op */ }
    fun isVertical(): Boolean = false

    private fun cycleSize() {
        size = size.next()
        onSizeCycledListener?.accept(size.token)
        rebuildAndInvalidate()
    }

    fun applyConfig(configString: String?) {
        if (configString.isNullOrEmpty()) return
        val cfg = KeyValueSet(configString)

        size = FusionSize.from(cfg.get("hudSize", FusionSize.STATS.token))
        showFPS = cfg.get("showFPS", "1") == "1"
        showEngine = cfg.get("showEngine", "1") == "1"
        showGpuModel = cfg.get("showGpuModel", "1") == "1"
        showCPU = cfg.get("showCPUUsage", cfg.get("showCPULoad", "1")) == "1"
        showGPU = cfg.get("showGPULoad", "1") == "1"
        showCpuTemp = cfg.get("showTemp", "1") == "1"
        showGpuTemp = cfg.get("showGpuTemp", "0") == "1"
        showVram = cfg.get("showVram", "1") == "1"
        showRAM = cfg.get("showRAM", "1") == "1"
        showBattery = cfg.get("showBattery", "1") == "1"
        showPower = cfg.get("showPower", "1") == "1"
        showBatteryTemp = cfg.get("showBatteryTemp", "0") == "1"
        showGraph = cfg.get("showFPSGraph", "0") == "1"
        showLow001 = cfg.get("showLow001", "1") == "1"
        fpsDecimal = cfg.get("fpsDecimal", "1") == "1"
        showClockTime = cfg.get("showClock", "1") == "1"   // Fusion clock defaults ON
        showPerCore = cfg.get("showPerCore", "1") == "1"
        showSwap = cfg.get("showSwap", "1") == "1"
        showNet = cfg.get("showNet", "1") == "1"
        showResolution = cfg.get("showResolution", "1") == "1"
        showProton = cfg.get("showProton", "1") == "1"
        showWrapper = cfg.get("showWrapper", "1") == "1"
        showDxVer = cfg.get("showDxVer", "1") == "1"
        showSession = cfg.get("showSession", "1") == "1"
        tempDisplay = HudMetrics.TempDisplay.from(cfg)
        lockController.setLocked(cfg.get("hudLocked", "0") == "1")

        colorIntensity = when (cfg.get("hudColor", "vivid")) {
            "soft" -> 0.72f; "mid" -> 0.88f; else -> 1.0f
        }
        outlineIntensity = (parseHudOutline(cfg.get("hudOutline", "40")) / 100f).coerceIn(0f, 1f)
        outlineFollowAccent = cfg.get("hudOutlineAccent", "0") == "1"
        scale = (parseIntOr(cfg.get("hudScale", "100"), 100).coerceIn(50, 200)) / 100f
        bgOpacity = (parseIntOr(cfg.get("hudOpacity", "62"), 62).coerceIn(0, 100)) / 100f

        rebuildAndInvalidate()
    }

    // ---- Lifecycle: self-refresh ------------------------------------------
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startUpdates()
    }

    override fun onDetachedFromWindow() {
        updateJob?.cancel(); updateJob = null
        super.onDetachedFromWindow()
    }

    private fun startUpdates() {
        if (updateJob?.isActive == true) return
        updateJob = scope.launch {
            while (isActive) {
                val rawFps = fpsCounter?.currentFPS ?: fpsProvider()
                val fps = if (rawFps.isFinite()) rawFps.coerceAtLeast(0f) else 0f
                val counter = fpsCounter
                val collected = withContext(Dispatchers.IO) {
                    Triple(
                        metrics.snapshot(),
                        counter?.frametimeLows ?: FpsCounter.FrametimeLows.EMPTY,
                        counter?.avgFPS ?: 0f,
                    )
                }
                snap = collected.first
                lows = collected.second
                fpsAvg = collected.third
                fpsNow = fps
                appendGraphSample(1000f / max(fps, 1f))
                rebuildAndInvalidate()
                delay(1000)
            }
        }
    }

    private fun appendGraphSample(ms: Float) {
        if (graphSamples.size >= GRAPH_CAP) graphSamples.removeFirst()
        graphSamples.addLast(if (ms.isFinite() && ms > 0f) ms else Float.NaN)
    }

    private fun rebuildAndInvalidate() {
        rebuild()
        requestLayout()
        invalidate()
    }

    // ---- Text helpers -----------------------------------------------------
    private fun blend(color: Int): Int {
        val i = colorIntensity.coerceIn(0f, 1f)
        if (i >= 0.999f) return color
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] *= i
        return Color.HSVToColor(Color.alpha(color), hsv)
    }

    private fun sp(v: Float) = v * density * scale
    private fun measure(text: String, sizePx: Float): Float { measurePaint.textSize = sizePx; return measurePaint.measureText(text) }
    private fun lineH(sizePx: Float): Float { measurePaint.textSize = sizePx; val fm = measurePaint.fontMetrics; return fm.descent - fm.ascent }
    private fun ascent(sizePx: Float): Float { measurePaint.textSize = sizePx; return measurePaint.fontMetrics.ascent }

    private fun runWidth(spans: List<Span>): Float {
        var w = 0f
        for (s in spans) w += measure(s.text, s.sizePx)
        return w
    }

    private fun placeRun(startX: Float, baseline: Float, spans: List<Span>): Float {
        var x = startX
        for (s in spans) {
            glyphs.add(Glyph(x, baseline, s.text, blend(s.color), s.sizePx, s.bold))
            x += measure(s.text, s.sizePx)
        }
        return x
    }

    private fun one(v: Float): String =
        if (fpsDecimal) String.format(Locale.US, "%.1f", v) else v.roundToInt().toString()

    // Shows "30->118" while something downstream of the game is adding frames.
    // The game's own rate stays visible next to the panel rate, because the gap
    // between the two is precisely what frame generation is doing.
    private fun fpsText(v: Float): String =
        if (differs(v)) "${one(v)}\u2192${one(presentedFps)}" else one(v)

    // Either direction: up = frames added (LSFG Native), down = frames lost
    // between the guest and the panel, which is the failure win-fg can hit
    // and which a single number hides completely.
    private fun differs(v: Float): Boolean =
        presentedFps > 0f && kotlin.math.abs(presentedFps - v) > maxOf(1f, v * 0.10f)

    private fun fmt1(v: Float): String = String.format(Locale.US, "%.1f", v)
    private fun lowText(v: Float): String? = if (v <= 0f) null else fmt1(v)

    // number (white, or "—" muted when null) + unit (muted, smaller)
    private fun numUnit(num: String?, unit: String, numPx: Float, unitPx: Float): List<Span> =
        if (num == null) listOf(Span("—", colDim, numPx), Span(unit, colDim, unitPx, false))
        else listOf(Span(num, colValue, numPx), Span(unit, colDim, unitPx, false))

    // "0.2GiB"/"75%"/"1938MHz" → white number + muted suffix
    private fun valueUnit(text: String, numPx: Float, unitPx: Float): List<Span> {
        val i = text.indexOfFirst { !(it.isDigit() || it == '.' || it == '-') }
        return if (i <= 0) listOf(Span(text, colValue, numPx))
        else listOf(Span(text.substring(0, i), colValue, numPx), Span(text.substring(i), colDim, unitPx, false))
    }

    private fun tempSpans(c: Int?, sensor: HudMetrics.TempSensor, numPx: Float, unitPx: Float): List<Span> {
        if (c == null) return emptyList()
        val txt = HudMetrics.formatTemp(c.toFloat(), tempDisplay, false) // "81°C"
        val col = HudMetrics.tempColor(
            c.toFloat(), metrics.resolveThresholds(sensor, tempDisplay), tempDisplay, colValue)
        val i = txt.indexOf('°')
        return if (i <= 0) listOf(Span(txt, col, numPx))
        else listOf(Span(txt.substring(0, i), col, numPx), Span(txt.substring(i), colDim, unitPx, false))
    }

    private fun gap(unitPx: Float) = Span("  ", colDim, unitPx, false)

    // ---- Wayland HDR state (one line of its own; nothing at all for FusionHdr.NONE) ----
    /** The state as a full line: "HDR", "HDR (no headroom)", "HDR off", "HDR ready", "HDR tone-mapped",
     *  "HDR not on this screen". */
    private fun hdrLine(px: Float): List<Span> = when (hdrState) {
        FusionHdr.ON -> listOf(Span("HDR", colHdr, px))
        FusionHdr.NO_HEADROOM -> listOf(Span("HDR", colHdr, px), Span(" (no headroom)", colBat, px))
        FusionHdr.OFF -> listOf(Span("HDR off", colDim, px))
        FusionHdr.READY -> listOf(Span("HDR ready", colDim, px))
        FusionHdr.TONEMAPPED -> listOf(Span("HDR", colDim, px), Span(" tone-mapped", colBat, px))
        FusionHdr.NOT_ON_THIS_SCREEN -> listOf(Span("HDR", colDim, px), Span(" not on this screen", colBat, px))
        else -> emptyList()
    }
    /** The value after an "HDR" label (Full / Mega): "on", "no headroom", "off", "ready", "tone-mapped",
     *  "not on this screen". */
    private fun hdrValue(px: Float): List<Span> = when (hdrState) {
        FusionHdr.ON -> listOf(Span("on", colHdr, px))
        FusionHdr.NO_HEADROOM -> listOf(Span("no headroom", colBat, px))
        FusionHdr.OFF -> listOf(Span("off", colDim, px))
        FusionHdr.READY -> listOf(Span("ready", colDim, px))
        FusionHdr.TONEMAPPED -> listOf(Span("tone-mapped", colBat, px))
        FusionHdr.NOT_ON_THIS_SCREEN -> listOf(Span("not on this screen", colBat, px))
        else -> emptyList()
    }
    private fun hdrText(): String = hdrLine(1f).joinToString("") { it.text }

    // ---- GPU model (with the Wayland GPU-name spoof) ----
    /** What the GPU row names: the spoofed GPU when this session reports one, else the real model. */
    private fun gpuNameText(): String = gpuSpoofName.ifBlank { gpuModel }
    /** The GPU row's value run. A spoofed name carries a marker in the same warning colour the HDR line
     *  qualifies its state with, so the row can't be read as the chip that is really rendering:
     *  "GeForce GTX 1080 (spoof)", and where the line is tightest (the pill) "GeForce GTX 1080 spoof". */
    private fun gpuModelSpans(px: Float, color: Int, compact: Boolean = false): List<Span> {
        val name = Span(gpuNameText(), color, px)
        if (gpuSpoofName.isBlank()) return listOf(name)
        return listOf(name, Span(if (compact) " spoof" else " (spoof)", colBat, px))
    }

    // ---- Layout builders --------------------------------------------------
    private fun rebuild() {
        glyphs.clear(); sepYs.clear(); bars.clear(); pendingRight.clear(); graphRect = null
        val s = snap
        if (s == null) { contentW = 0f; contentH = 0f; return }
        buildLevel(s)
    }

    // ---- Shared small-info helpers ----------------------------------------
    private fun clockTimeString(): String = DateFormat.getTimeFormat(context).format(Date())

    private fun elapsedString(): String {
        val sec = (fpsCounter?.sessionLengthSec ?: 0f).toInt().coerceAtLeast(0)
        val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    /**
     * The FPS row's API/engine label — the DX→Vulkan translator ([engineLabel]: "DXVK"/"VKD3D"/"Zink"),
     * gated by the "Engine" chip. This is short and shows in EVERY size. Falls back to a bare "FPS" when
     * the toggle is off or no engine label is set. (The graphics WRAPPER is separate — Mega-only, drawn
     * below the frametime graph — not on the FPS row.)
     */
    private fun apiLabel(): String =
        if (showEngine && engineLabel.isNotBlank()) engineLabel else "FPS"

    /** The DX-wrapper version WITH its engine name (e.g. "DXVK 2.4.1", "VKD3D 2.14"), or "" if none.
     *  Used for the Mega stack line below the graph. */
    private fun dxVersionLabeled(): String = when {
        engineLabel.contains("VKD3D", true) && vkd3dVersion.isNotBlank() -> "VKD3D $vkd3dVersion"
        engineLabel.contains("DXVK", true) && dxvkVersion.isNotBlank() -> "DXVK $dxvkVersion"
        else -> ""
    }

    /** The DX-wrapper version for the current engine, or "" (used for the Tiles "API" tile sub-line). */
    private fun dxVersion(): String = when {
        engineLabel.contains("VKD3D", true) -> vkd3dVersion
        engineLabel.contains("DXVK", true) -> dxvkVersion
        else -> ""
    }

    /** Resolution + refresh, e.g. "1280x720 @ 120Hz". Read from the current display (Main thread). */
    private fun resolutionString(): String {
        val dm = resources.displayMetrics
        val hz = (display?.refreshRate ?: 0f)
        val base = "${dm.widthPixels}x${dm.heightPixels}"
        return if (hz >= 1f) "$base @ ${hz.roundToInt()}Hz" else base
    }

    /** Places a column of "label + value" rows; returns (bottomY, rightX). Appends glyphs. Rows flagged
     *  [HudRow.inline] opt out of the shared label column (value follows the label directly). A row's
     *  [HudRow.right] group is queued for the panel's right edge ([pendingRight]); the returned rightX
     *  already makes room for it. */
    private fun layoutColumn(rows: List<HudRow>, x: Float, y0: Float,
                             rowPx: Float, lineGap: Float, lvGap: Float): Pair<Float, Float> {
        if (rows.isEmpty()) return Pair(y0, x)
        var labelCol = 0f
        for (r in rows) if (!r.inline) labelCol = max(labelCol, measure(r.label.text, rowPx))
        val h = lineH(rowPx); val asc = ascent(rowPx)
        val rightGap = sp(12f)
        var y = y0; var right = x
        for (r in rows) {
            val baseline = y - asc
            placeRun(x, baseline, listOf(r.label))
            val valX = if (r.inline) x + measure(r.label.text, rowPx) + lvGap
                       else x + labelCol + lvGap
            val end = placeRun(valX, baseline, r.vals)
            right = max(right, end)
            if (r.right.isNotEmpty()) {
                right = max(right, end + rightGap + runWidth(r.right))
                pendingRight.add(RightRun(baseline, r.right))
            }
            y += h + lineGap
        }
        return Pair(y - lineGap, right)
    }

    /** Places wrapping " · "-separated info fragments within maxWidth; returns (bottomY, rightX). */
    private fun layoutWrap(fragments: List<List<Span>>, x: Float, y0: Float, maxWidth: Float,
                           px: Float, lineGap: Float): Pair<Float, Float> {
        if (fragments.isEmpty()) return Pair(y0, x)
        val h = lineH(px); val asc = ascent(px)
        val sepW = measure(" · ", px)
        var cx = x; var y = y0; var right = x; var firstOnLine = true
        for (frag in fragments) {
            val w = runWidth(frag)
            if (!firstOnLine && (cx + sepW + w - x) > maxWidth) { y += h + lineGap; cx = x; firstOnLine = true }
            if (!firstOnLine) { placeRun(cx, y - asc, listOf(Span(" · ", colDim, px))); cx += sepW }
            val end = placeRun(cx, y - asc, frag)
            cx = end; right = max(right, end); firstOnLine = false
        }
        return Pair(y + h, right)
    }

    /** The FPS number's colour, like the Steam Deck overlay: green from 55, amber from 30, red below. */
    private fun fpsColor(fps: Float): Int = when {
        fps >= 55f -> 0xFF3DDC84.toInt()
        fps >= 30f -> 0xFFF5C542.toInt()
        else -> 0xFFFF5D5D.toInt()
    }

    /** "label value" with the label in the metric's colour, e.g. "0.1% 47.9". */
    private fun labelled(label: String, color: Int, rowPx: Float, value: List<Span>): List<Span> =
        listOf(Span("$label ", color, rowPx)) + value

    /**
     * Builds the panel for the current level ([FusionSize.level]); each level contains the one before it.
     * The layout follows the mock-up: a coloured label per metric, white values, dim regular-weight units,
     * and a second group pushed to the right edge of the row (temperature and clock on the GPU and CPU
     * rows, RAM beside VRAM, the engine tag beside the FPS number).
     *  1  the FPS number and the engine tag
     *  2  the frametime graph, the frametime and battery watts
     *  3  GPU, CPU, VRAM and RAM, and power rows
     *  4  per-core load, the lows, GPU model, display, API, wrapper, Proton, resolution, network, swap, time
     */
    private fun buildLevel(s: HudMetrics.Snapshot) {
        val level = size.level
        val rowPx = sp(12f); val unitPx = rowPx
        val bigPx = sp(30f); val tagPx = sp(11f); val bandPx = sp(10.5f)
        val pad = sp(10f); val lineGap = sp(2f); val lvGap = sp(8f); val rightGap = sp(12f)
        padPx = pad
        var y = sp(8f)
        var maxRight = pad + sp(130f)   // panel minimum width, like the mock-up

        // ---- Level 1: the big FPS number, "FPS", and the engine tag at the right edge ----
        if (showFPS) {
            val shown = if (presentedFps > 0f) presentedFps else fpsNow
            val baseline = y - ascent(bigPx)
            var x = placeRun(pad, baseline, listOf(Span(fpsText(fpsNow), fpsColor(shown), bigPx)))
            x = placeRun(x + sp(6f), baseline, listOf(Span("FPS", colValue, tagPx)))
            maxRight = max(maxRight, x)
            if (showEngine && engineLabel.isNotBlank()) {
                val eng = listOf(Span(engineLabel, colFps, rowPx))
                maxRight = max(maxRight, x + rightGap + runWidth(eng))
                pendingRight.add(RightRun(baseline, eng))
            }
            y += lineH(bigPx) + lineGap
        }

        // ---- Level 2: frametime graph (its width is settled once the panel's width is known), then the
        // frametime in ms with the battery draw in watts at the right ----
        var graphTop = -1f
        val graphH = sp(34f)
        if (level >= 2 && showFPS) {
            if (showGraph) { graphTop = y; y += graphH + lineGap }
            val baseline = y - ascent(rowPx)
            val left = labelled("FRM", colGraph, rowPx, numUnit(fmt1(1000f / max(fpsNow, 1f)), " ms", rowPx, unitPx))
            val x = placeRun(pad, baseline, left)
            maxRight = max(maxRight, x)
            if (showPower && s.battery.watts > 0f) {
                val r = labelled("BAT", colBat, rowPx, numUnit(fmt1(s.battery.watts), " W", rowPx, unitPx))
                maxRight = max(maxRight, x + rightGap + runWidth(r))
                pendingRight.add(RightRun(baseline, r))
            }
            y += lineH(rowPx) + lineGap
        }

        // ---- Level 3: the stat rows ----
        if (level >= 3) {
            val rows = ArrayList<HudRow>()
            if (showGPU) {
                val r = ArrayList<Span>()
                if (showGpuTemp) r += tempSpans(s.gpuTempC, HudMetrics.TempSensor.GPU, rowPx, unitPx)
                if (r.isNotEmpty()) r += gap(unitPx)
                r += numUnit(s.gpuClockMhz?.toString(), " MHz", rowPx, unitPx)
                rows.add(HudRow(Span("GPU", colGpu, rowPx), numUnit(s.gpuPercent?.toString(), " %", rowPx, unitPx), right = r))
            }
            if (showCPU) {
                val r = ArrayList<Span>()
                if (showCpuTemp) r += tempSpans(s.cpuTempC, HudMetrics.TempSensor.CPU, rowPx, unitPx)
                if (r.isNotEmpty()) r += gap(unitPx)
                r += numUnit(s.cpuClockMhz?.toString(), " MHz", rowPx, unitPx)
                rows.add(HudRow(Span("CPU", colCpu, rowPx), numUnit(s.cpuPercent?.toString(), " %", rowPx, unitPx), right = r))
            }
            val hasVram = showVram && s.vramText() != null
            if (hasVram) {
                val r = if (showRAM) labelled("RAM", colRam, rowPx, valueUnit(s.ramUsedText(), rowPx, unitPx)) else emptyList()
                rows.add(HudRow(Span("VRAM", colVram, rowPx), valueUnit(s.vramText()!!, rowPx, unitPx), right = r))
            } else if (showRAM) {
                rows.add(HudRow(Span("RAM", colRam, rowPx), valueUnit(s.ramUsedText(), rowPx, unitPx)))
            }
            val percent = if (showBattery) s.battery.percent else null
            val batRight = ArrayList<Span>()
            if (percent != null) { batRight += numUnit(percent.toString(), " % battery", rowPx, unitPx) }
            if (showBatteryTemp) tempSpans(s.battery.tempC, HudMetrics.TempSensor.BATTERY, rowPx, unitPx)
                .let { if (it.isNotEmpty()) { if (batRight.isNotEmpty()) batRight += gap(unitPx); batRight += it } }
            if (showPower && s.battery.watts > 0f) {
                rows.add(HudRow(Span("PWR", colBat, rowPx), numUnit(fmt1(s.battery.watts), " W", rowPx, unitPx), right = batRight))
            } else if (batRight.isNotEmpty()) {
                rows.add(HudRow(Span("BAT", colBat, rowPx), batRight))
            }
            if (rows.isNotEmpty()) {
                sepYs.add(y); y += lineGap + sp(3f)
                val (bottom, right) = layoutColumn(rows, pad, y, rowPx, lineGap, lvGap)
                y = bottom + lineGap; maxRight = max(maxRight, right)
            }
        }

        // ---- Level 4: per-core grid, then the detail rows ----
        if (level >= 4) {
            if (showPerCore) {
                val pct = s.perCorePercent
                val n = pct.size
                if (n > 0) {
                    sepYs.add(y); y += lineGap + sp(3f)
                    val cols = if (n >= 4) 4 else n
                    val avail = max(maxRight - pad, sp(190f))
                    val cellW = avail / cols
                    val h = lineH(bandPx); val barH = sp(3f)
                    val cellH = h + barH + sp(3f)
                    for (i in 0 until n) {
                        val cx = pad + (i % cols) * cellW
                        val cy = y + (i / cols) * (cellH + lineGap)
                        val load = if (pct[i] >= 0) pct[i] else 0
                        val end = placeRun(cx, cy - ascent(bandPx),
                            listOf(Span("C$i ", colDim, bandPx, false), Span(if (pct[i] >= 0) pct[i].toString() else "—", colValue, bandPx)))
                        maxRight = max(maxRight, end)
                        bars.add(Bar(RectF(cx, cy + h + sp(1f), cx + cellW - sp(8f), cy + h + sp(1f) + barH),
                            (load / 100f).coerceIn(0f, 1f)))
                    }
                    val coreRows = (n + cols - 1) / cols
                    y += coreRows * (cellH + lineGap)
                    maxRight = max(maxRight, pad + avail)
                }
            }

            val rows = ArrayList<HudRow>()
            if (showFPS) {
                val r = ArrayList<Span>()
                r += labelled("0.1%", colGraph, rowPx, numUnit(lowText(lows.low01Fps), "", rowPx, unitPx))
                if (showLow001) { r += gap(unitPx); r += labelled("0.01%", colGraph, rowPx, numUnit(lowText(lows.low001Fps), "", rowPx, unitPx)) }
                rows.add(HudRow(Span("1%", colGraph, rowPx), numUnit(lowText(lows.low1Fps), "", rowPx, unitPx), right = r))
            }
            if (showGpuModel && gpuNameText().isNotBlank())
                rows.add(HudRow(Span("GPU", colGpu, rowPx), gpuModelSpans(rowPx, colValue)))
            if (displayServer.isNotBlank())
                rows.add(HudRow(Span("DISP", colDisp, rowPx), listOf(Span(displayServer, colValue, rowPx))))
            if (hdrState != FusionHdr.NONE)
                rows.add(HudRow(Span("HDR", colHdr, rowPx), hdrValue(rowPx)))
            val dxLine = dxVersionLabeled()
            if (showDxVer && dxLine.isNotBlank())
                rows.add(HudRow(Span("API", colFps, rowPx), listOf(Span(dxLine, colValue, rowPx))))
            if (showWrapper && graphicsWrapper.isNotBlank())
                rows.add(HudRow(Span("WRAP", colFps, rowPx), listOf(Span(graphicsWrapper, colValue, rowPx))))
            if (showProton && wineVersion.isNotBlank())
                rows.add(HudRow(Span("WINE", colFps, rowPx), listOf(Span(wineVersion, colValue, rowPx))))
            if (showResolution)
                rows.add(HudRow(Span("RES", colCpu, rowPx), listOf(Span(resolutionString(), colValue, rowPx))))
            val hasNet = showNet && s.netDownBps != null
            val hasSwap = showSwap && s.swapUsedText() != null
            if (hasNet || hasSwap) {
                val swapRun = ArrayList<Span>()
                if (hasSwap) {
                    swapRun += Span("SWP ", colRam, rowPx); swapRun += valueUnit(s.swapUsedText()!!, rowPx, unitPx)
                }
                if (hasNet) {
                    val d = (s.netDownBps ?: 0L) / 1024L
                    val u = (s.netUpBps ?: 0L) / 1024L
                    val v = ArrayList<Span>()
                    v += Span("↓ ", colDim, unitPx, false); v += Span(d.toString(), colValue, rowPx)
                    v += Span(" ↑ ", colDim, unitPx, false); v += Span(u.toString(), colValue, rowPx)
                    v += Span(" KB/s", colDim, unitPx, false)
                    rows.add(HudRow(Span("NET", colRam, rowPx), v, right = swapRun))
                } else {
                    rows.add(HudRow(Span("SWP", colRam, rowPx), swapRun.drop(1)))
                }
            }
            if (showSession || showClockTime) {
                val clock = Span(clockTimeString(), colDim, rowPx, false)
                if (showSession)
                    rows.add(HudRow(Span("TIME", colDim, rowPx), listOf(Span(elapsedString(), colValue, rowPx)),
                        right = if (showClockTime) listOf(clock) else emptyList()))
                else
                    rows.add(HudRow(Span("TIME", colDim, rowPx), listOf(clock)))
            }
            if (rows.isNotEmpty()) {
                sepYs.add(y); y += lineGap + sp(3f)
                val (bottom, right) = layoutColumn(rows, pad, y, rowPx, lineGap, lvGap)
                y = bottom + lineGap; maxRight = max(maxRight, right)
            }
        }

        if (graphTop >= 0f) graphRect = RectF(pad, graphTop, maxRight, graphTop + graphH)
        // Right-aligned groups, now that the panel's width is known.
        for (rr in pendingRight) placeRun(maxRight - runWidth(rr.spans), rr.baseline, rr.spans)
        contentW = maxRight + pad
        contentH = y - lineGap + sp(9f)
    }

    // ---- Measure / draw ---------------------------------------------------
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(contentW.roundToInt(), widthMeasureSpec),
            resolveSize(contentH.roundToInt(), heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (contentW <= 0f || contentH <= 0f) { lockController.drawBadge(canvas); return }

        val radius = sp(8f)
        bgPaint.color = Color.argb((bgOpacity.coerceIn(0f, 1f) * 255f).roundToInt(), 8, 10, 16)
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, bgPaint)

        // Outline: width from the outline slider (0 = none), colour = accent or grey per the
        // "Outline color" toggle.
        val strokeW = if (outlineIntensity > 0f) outlineIntensity * sp(3.5f) else 0f
        if (strokeW > 0f) {
            strokePaint.strokeWidth = strokeW
            strokePaint.color = if (outlineFollowAccent) AppThemeState.getCurrentAccentArgb()
                                else Color.argb(48, 255, 255, 255)
            val h = strokeW / 2f
            canvas.drawRoundRect(h, h, width - h, height - h, radius, radius, strokePaint)
        }

        // Dividers between blocks
        if (sepYs.isNotEmpty()) {
            tilePaint.color = Color.argb(31, 255, 255, 255)
            val sepH = max(1f, density)
            for (sy in sepYs) canvas.drawRect(padPx, sy, width - padPx, sy + sepH, tilePaint)
        }

        // Per-core bars: a faint track and the load on top
        for (b in bars) {
            tilePaint.color = Color.argb(46, 255, 255, 255)
            canvas.drawRoundRect(b.rect, b.rect.height() / 2f, b.rect.height() / 2f, tilePaint)
            tilePaint.color = blend(colCpu)
            val fill = RectF(b.rect.left, b.rect.top, b.rect.left + b.rect.width() * b.frac, b.rect.bottom)
            canvas.drawRoundRect(fill, fill.height() / 2f, fill.height() / 2f, tilePaint)
        }

        // Text
        for (g in glyphs) {
            drawPaint.textSize = g.sizePx
            drawPaint.typeface = if (g.bold) boldFace else regularFace
            drawPaint.color = g.color
            canvas.drawText(g.text, g.x, g.baseline, drawPaint)
        }

        // Frametime graph
        graphRect?.let { drawGraph(canvas, it) }

        lockController.drawBadge(canvas)
    }

    private fun drawGraph(canvas: Canvas, rect: RectF) {
        val values = graphSamples.toList().filter { it.isFinite() }
        if (values.size < 2) return
        var peak = 33.3f   // a 30 fps frametime, so a steady game does not fill the whole graph
        for (v in values) peak = max(peak, v)
        graphPaint.strokeWidth = sp(1.6f)
        graphPaint.color = blend(colGraph)
        val step = rect.width() / (values.size - 1)
        val path = Path()
        for (i in values.indices) {
            val px = rect.left + i * step
            val py = rect.bottom - (values[i] / peak).coerceIn(0f, 1f) * rect.height()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        canvas.drawPath(path, graphPaint)
        val lastY = rect.bottom - (values.last() / peak).coerceIn(0f, 1f) * rect.height()
        tilePaint.color = blend(colGraph)
        canvas.drawCircle(rect.right, lastY, sp(2.5f), tilePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = lockController.onTouchEvent(event)

    private companion object {
        const val GRAPH_CAP = 60

        fun parseIntOr(s: String?, d: Int): Int = try { s?.trim()?.toInt() ?: d } catch (e: Exception) { d }

    }
}
