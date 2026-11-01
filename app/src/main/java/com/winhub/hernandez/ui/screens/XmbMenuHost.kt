package com.winhub.hernandez.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.winhub.hernandez.ui.theme.DangerRed
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// Rendering + input for the nested XMB menus (model in XmbModel.kt). The Games-tab XMB owns one
// [XmbNavState]; when it has menus, the games bar fades out and [XmbNestedLayer] draws:
//   breadcrumb (game › Settings › General) · an icon strip per level behind you · the active column,
//   whose highlighted row stays at a fixed height while the list runs past it · a choices column for
//   Choice rows · an inline keyboard field for Text rows · or a custom XmbPanel.
// Keys come from the XMB's single focus root via [XmbNavState.onKey].
// ─────────────────────────────────────────────────────────────────────────────────────────────────

internal class XmbPicker(val row: XmbRow.Choice, sel: Int) {
    var sel by mutableIntStateOf(sel)
}

internal class XmbNavState {
    val stack = mutableStateListOf<XmbMenu>()
    var picker by mutableStateOf<XmbPicker?>(null)
    var editingKey by mutableStateOf<String?>(null)
    /** The text being typed, cursor starting at the END (so Backspace edits what's there). */
    var editValue by mutableStateOf(TextFieldValue(""))
    /** Bumped after every change so rows that read plain values (extras) rebuild. */
    var revision by mutableIntStateOf(0)
    var savedAt by mutableLongStateOf(0L)
    /** Hands D-pad focus back to the XMB root after the keyboard closes. */
    var refocus: () -> Unit = {}

    val depth: Int get() = stack.size
    val top: XmbMenu? get() = stack.lastOrNull()

    fun push(m: XmbMenu) { picker = null; editingKey = null; stack.add(m) }
    fun pop() {
        if (picker != null) { picker = null; return }
        if (editingKey != null) { editingKey = null; refocus(); return }
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex).onClose?.invoke()
    }
    fun popTo(d: Int) {
        picker = null; editingKey = null
        while (stack.size > max(0, d)) stack.removeAt(stack.lastIndex).onClose?.invoke()
    }
    fun bump() { revision++ }
}

/** A confirm step as its own column; opens on Cancel so a stray A can't confirm. */
internal fun xmbConfirmMenu(nav: XmbNavState, c: XmbConfirm, onOk: () -> Unit): XmbMenu =
    XmbMenu(c.title, Icons.Filled.Info, initialKey = "cancel") {
        listOf(
            XmbRow.Info("msg", c.message, Icons.Filled.Info),
            XmbRow.Action("ok", c.okLabel, if (c.danger) Icons.Filled.Delete else Icons.Filled.Check, danger = c.danger) { nav.pop(); onOk(); nav.bump() },
            XmbRow.Action("cancel", "Cancel", Icons.Filled.Close) { nav.pop() },
        )
    }

/** A tap target that never takes D-pad focus: the XMB's single root owns the keys, and a focused row
 *  that scrolls out of composition would strand focus (Android then parks it on the top bar). */
internal fun Modifier.xmbClick(src: MutableInteractionSource, onClick: () -> Unit): Modifier =
    this.focusProperties { canFocus = false }.clickable(src, null, onClick = onClick)

// ── row helpers ──
private fun XmbRow.disabled(): String? = when (this) {
    is XmbRow.Toggle -> disabledReason
    is XmbRow.Choice -> disabledReason
    is XmbRow.Text -> disabledReason
    is XmbRow.Slider -> disabledReason
    is XmbRow.Link -> disabledReason
    is XmbRow.Action -> disabledReason
    is XmbRow.Header, is XmbRow.Info, is XmbRow.External -> null
}
private fun XmbRow.sub(): String? = disabled() ?: when (this) {
    is XmbRow.Toggle -> subtitle
    is XmbRow.Choice -> subtitle
    is XmbRow.Text -> subtitle
    is XmbRow.Slider -> subtitle
    is XmbRow.Link -> subtitle
    is XmbRow.Action -> subtitle
    is XmbRow.Info -> subtitle
    is XmbRow.External -> subtitle
    is XmbRow.Header -> null
}
private fun XmbRow.iconOrNull(): ImageVector? = when (this) {
    is XmbRow.Toggle -> icon
    is XmbRow.Choice -> icon
    is XmbRow.Text -> icon
    is XmbRow.Slider -> icon
    is XmbRow.Link -> icon
    is XmbRow.Action -> icon
    is XmbRow.Info -> icon
    is XmbRow.External -> icon
    is XmbRow.Header -> null
}
private fun XmbRow.adjustable() = disabled() == null && (this is XmbRow.Toggle || this is XmbRow.Choice || this is XmbRow.Slider)

/** Rows of [m] for this frame (reads [XmbNavState.revision] so plain-value changes rebuild them). */
internal fun XmbNavState.rowsOf(m: XmbMenu, xmb: XmbScope): List<XmbRow> {
    @Suppress("UNUSED_VARIABLE") val r = revision
    return m.rows(xmb)
}

/** Index of the highlighted row without writing state (safe during composition). */
private fun effectiveSel(m: XmbMenu, rows: List<XmbRow>): Int {
    val i = rows.indexOfFirst { it.key == m.selKey }
    if (i >= 0) return i
    val firstActive = rows.indexOfFirst { it !is XmbRow.Header && it !is XmbRow.Info }
    return if (firstActive >= 0) firstActive else rows.indexOfFirst { it !is XmbRow.Header }
}

private fun XmbNavState.step(m: XmbMenu, rows: List<XmbRow>, d: Int) {
    var j = effectiveSel(m, rows) + d
    while (j in rows.indices) {
        if (rows[j] !is XmbRow.Header) { m.selKey = rows[j].key; return }
        j += d
    }
}

private fun XmbNavState.commitChoice(row: XmbRow.Choice, value: String, xmb: XmbScope) {
    if (value in row.disabledOptions) { xmb.toast("Not available here"); return }
    picker = null
    if (value == row.selected) return
    val c = row.confirm?.invoke(value)
    if (c != null) xmb.confirm(c) { row.onSelect(value); bump() } else { row.onSelect(value); bump() }
}

private fun XmbNavState.adjust(row: XmbRow, d: Int, xmb: XmbScope) {
    when (row) {
        is XmbRow.Toggle -> row.onChange(!row.value)
        is XmbRow.Slider -> {
            val v = (row.value + d * row.step).coerceIn(row.min, row.max)
            val rounded = Math.round(v * 100f) / 100f
            if (rounded != row.value) row.onChange(rounded)
        }
        is XmbRow.Choice -> {
            val i = row.options.indexOf(row.selected)
            var n = (if (i < 0) 0 else i) + d
            while (n in row.options.indices && row.options[n] in row.disabledOptions) n += d
            if (n in row.options.indices && n != i) commitChoice(row, row.options[n], xmb)
        }
        else -> {}
    }
    bump()
}

private fun XmbNavState.activate(row: XmbRow?, xmb: XmbScope) {
    if (row == null || row is XmbRow.Header || row is XmbRow.Info) return
    row.disabled()?.let { xmb.toast(it); return }
    when (row) {
        is XmbRow.Link -> push(row.open())
        is XmbRow.Choice -> picker = XmbPicker(row, row.options.indexOf(row.selected).coerceAtLeast(0))
        is XmbRow.Toggle, is XmbRow.Slider -> adjust(row, 1, xmb)
        is XmbRow.Text -> { editValue = TextFieldValue(row.value, TextRange(row.value.length)); editingKey = row.key }
        is XmbRow.Action -> { row.onClick(); bump() }
        is XmbRow.External -> row.onOpen()
        is XmbRow.Header, is XmbRow.Info -> {}
    }
}

internal fun XmbNavState.commitEdit(xmb: XmbScope) {
    val m = top ?: return
    val row = rowsOf(m, xmb).firstOrNull { it.key == editingKey } as? XmbRow.Text
    editingKey = null
    refocus()
    if (row != null) {
        val v = editValue.text.trim()
        if (v != row.value) row.onCommit(v)
        bump()
    }
}

private fun XmbNavState.switchSibling(d: Int) {
    val m = top ?: return
    val s = m.siblings ?: return
    val n = ((s.index + d) % s.count + s.count) % s.count
    val next = s.make(n)
    next.siblings = s.let { XmbSiblings(n, it.count, it.make, it.parentKey) }
    m.onClose?.invoke()
    picker = null; editingKey = null
    stack[stack.lastIndex] = next
    if (stack.size >= 2) stack[stack.lastIndex - 1].selKey = s.parentKey(n)
}

/** One key press while menus are open. Always consumes (the XMB owns the keys while nested). */
internal fun XmbNavState.onKey(k: XmbKey, xmb: XmbScope): Boolean {
    val m = top ?: return false
    if (editingKey != null) {
        when (k) { XmbKey.A -> commitEdit(xmb); XmbKey.B -> pop(); else -> {} }
        return true
    }
    m.panel?.let { p ->
        if (p.onKey(k)) return true
        if (k == XmbKey.B) { pop(); return true }
        return !p.passthrough
    }
    picker?.let { p ->
        when (k) {
            XmbKey.Up -> p.sel = max(0, p.sel - 1)
            XmbKey.Down -> p.sel = min(p.row.options.lastIndex, p.sel + 1)
            XmbKey.A -> p.row.options.getOrNull(p.sel)?.let { commitChoice(p.row, it, xmb) }
            XmbKey.B, XmbKey.Left -> picker = null
            else -> {}
        }
        return true
    }
    val rows = rowsOf(m, xmb)
    val row = rows.getOrNull(effectiveSel(m, rows))
    when (k) {
        XmbKey.Up -> step(m, rows, -1)
        XmbKey.Down -> step(m, rows, 1)
        XmbKey.Left -> if (row != null && row.adjustable()) adjust(row, -1, xmb) else pop()
        XmbKey.Right -> if (row != null && row.adjustable()) adjust(row, 1, xmb) else if (row is XmbRow.Link) activate(row, xmb)
        XmbKey.A -> activate(row, xmb)
        XmbKey.B -> pop()
        XmbKey.L1 -> switchSibling(-1)
        XmbKey.R1 -> switchSibling(1)
    }
    return true
}

/** What the A hint should say, and whether ◀▶ / L1 R1 apply right now. */
internal data class XmbHint(val a: String, val change: Boolean, val sections: Boolean)
internal fun XmbNavState.hint(xmb: XmbScope): XmbHint {
    val m = top ?: return XmbHint("Select", false, false)
    if (editingKey != null) return XmbHint("Done", false, false)
    if (picker != null) return XmbHint("Choose", false, false)
    m.panel?.let { p -> return XmbHint(if (p.passthrough) "Back" else "Select", false, m.siblings != null) }
    val rows = rowsOf(m, xmb)
    val row = rows.getOrNull(effectiveSel(m, rows))
    val a = when (row) {
        is XmbRow.Toggle -> "Toggle"
        is XmbRow.Link, is XmbRow.Choice -> "Open"
        is XmbRow.Text -> "Edit"
        is XmbRow.External -> "Open ↗"
        else -> "Select"
    }
    return XmbHint(a, row != null && row.adjustable(), m.siblings != null)
}

// ── rendering ──

private const val STRIP = 46f

/**
 * Draws the open menus. [w]/[h] are the content box in dp (below any see-through top bar).
 * [rootIcons]/[rootSel] are the game's options column, shown as the first icon strip.
 */
@Composable
internal fun XmbNestedLayer(
    nav: XmbNavState,
    xmb: XmbScope,
    w: Float,
    h: Float,
    port: Boolean,
    accent: Color,
    gameName: String,
    gameIcon: Bitmap?,
    rootIcons: List<ImageVector>,
    rootSel: Int,
    /** Where the open column's picked row sits (dp from the top); null = the XMB's own spot. A host whose options column lies elsewhere passes it. */
    anchorY: Float? = null,
    /** Where the first icon strip starts (dp from the left). */
    baseX: Float = 16f,
    /**
     * The game's options column stays on screen beside the open menus (a host that keeps it drawn): no root icon strip and no
     * breadcrumb, the first column starts at [baseX] + 8, and only the "Saved" flash is shown.
     */
    besideRoot: Boolean = false,
) {
    val m = nav.top ?: return
    val top = 70f
    val bottom = h - 10f
    val rowH = if (port) 44f else 40f
    val anchor = anchorY ?: (top + (bottom - top) * (if (port) 0.2f else 0.28f))
    val strips = if (besideRoot) nav.depth - 1 else nav.depth
    val x0 = baseX + strips * STRIP + 8f
    val colW = max(160f, min(if (port) w - x0 - 12f else 560f, w - x0 - 16f))
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize()) {
        // breadcrumb — tap a step to go back to it
        if (besideRoot) {
            val flash = nav.savedAt
            var showSaved by remember { mutableStateOf(false) }
            LaunchedEffect(flash) {
                if (flash > 0L && System.currentTimeMillis() - flash < 1500L) { showSaved = true; kotlinx.coroutines.delay(1200); showSaved = false }
            }
            val sa by animateFloatAsState(if (showSaved) 1f else 0f, tween(250), label = "xmbSavedBeside")
            Text("✓ Saved", color = Color(0xFF3DDC84), fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 16.dp).graphicsLayer { alpha = sa })
        } else Row(
            Modifier.offset(16.dp, 30.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(width = 22.dp, height = 32.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF222222))) {
                if (gameIcon != null) Image(remember(gameIcon) { gameIcon.asImageBitmap() }, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            val tap = remember { MutableInteractionSource() }
            Text(gameName, color = Color(0xFFBBBBBB), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.xmbClick(tap) { nav.popTo(0) })
            nav.stack.forEachIndexed { i, menu ->
                Text("›", color = Color(0xFF888888), fontSize = 14.sp)
                val t = remember { MutableInteractionSource() }
                Text(menu.title, color = if (i == nav.depth - 1) Color.White else Color(0xFFBBBBBB), fontSize = 14.sp,
                    fontWeight = if (i == nav.depth - 1) FontWeight.Medium else FontWeight.Normal, maxLines = 1,
                    modifier = Modifier.xmbClick(t) { nav.popTo(i + 1) })
            }
            val flash = nav.savedAt
            var showSaved by remember { mutableStateOf(false) }
            LaunchedEffect(flash) {
                if (flash > 0L && System.currentTimeMillis() - flash < 1500L) { showSaved = true; kotlinx.coroutines.delay(1200); showSaved = false }
            }
            val sa by animateFloatAsState(if (showSaved) 1f else 0f, tween(250), label = "xmbSaved")
            Text("✓ Saved", color = Color(0xFF3DDC84), fontSize = 11.sp, modifier = Modifier.graphicsLayer { alpha = sa })
        }

        // icon strips: the game's options, then each menu behind the active one
        if (!besideRoot) XmbStrip(rootIcons, rootSel, baseX, anchor, rowH, accent) { nav.popTo(0) }
        for (i in 0 until nav.depth - 1) {
            val menu = nav.stack[i]
            val rows = nav.rowsOf(menu, xmb).filter { it !is XmbRow.Header }
            val sel = rows.indexOfFirst { it.key == menu.selKey }.coerceAtLeast(0)
            XmbStrip(rows.map { it.iconOrNull() ?: rowIconFallback }, sel, baseX + (if (besideRoot) i else i + 1) * STRIP, anchor, rowH, accent) { nav.popTo(i + 1) }
        }

        val panel = m.panel
        if (panel != null) {
            panel.Content(Modifier.offset(x0.dp, top.dp).width((w - x0 - 16f).dp).height((bottom - top).dp))
        } else {
            // active column
            val rows = nav.rowsOf(m, xmb)
            val si = effectiveSel(m, rows)
            fun hOf(r: XmbRow, i: Int) = when {
                r is XmbRow.Header -> 26f
                // A cover tile is 2:3 and needs the row to grow with it.
                r is XmbRow.Action && r.thumbnailLarge && r.thumbnail != null -> rowH + 48f
                r is XmbRow.Info && r.value.isEmpty() && r.label.length > 34 -> rowH + 16f
                i == si && r.sub() != null -> rowH + 14f
                else -> rowH
            }
            val ys = FloatArray(rows.size)
            if (si >= 0) {
                ys[si] = anchor
                for (i in si + 1 until rows.size) ys[i] = ys[i - 1] + hOf(rows[i - 1], i - 1) + 2f
                for (i in si - 1 downTo 0) ys[i] = ys[i + 1] - hOf(rows[i], i) - 2f
            }
            var vAcc by remember { mutableFloatStateOf(0f) }
            val stepPx = with(density) { 34.dp.toPx() }
            val vDrag = rememberDraggableState { d ->
                vAcc += d
                while (vAcc <= -stepPx) { vAcc += stepPx; nav.onKey(XmbKey.Down, xmb) }
                while (vAcc >= stepPx) { vAcc -= stepPx; nav.onKey(XmbKey.Up, xmb) }
            }
            Box(
                Modifier.offset(x0.dp, 0.dp).width(colW.dp).height(h.dp)
                    .draggable(vDrag, Orientation.Vertical, onDragStarted = { vAcc = 0f })
                    .graphicsLayer { alpha = if (nav.picker != null) 0.35f else 1f },
            ) {
                rows.forEachIndexed { i, r ->
                    val y = ys[i]
                    val hh = hOf(r, i)
                    if (y + hh >= top - 30f && y <= bottom + 10f) {
                    var o = 1f
                    if (y < top) o = max(0f, 1f - (top - y) / 14f)
                    if (y + hh > bottom) o = max(0f, 1f - (y + hh - bottom) / 40f)
                    if (i != si && r !is XmbRow.Header) o *= max(0.45f, 1f - abs(i - si) * 0.07f)
                    if (r.disabled() != null) o *= 0.5f
                    key(r.key) {
                        val ay by animateFloatAsState(y, spring(dampingRatio = 0.9f, stiffness = 500f), label = "xmbNy")
                        val ao by animateFloatAsState(o, tween(220), label = "xmbNa")
                        val src = remember { MutableInteractionSource() }
                        XmbRowView(
                            row = r, selected = i == si, editing = i == si && nav.editingKey == r.key,
                            nav = nav, xmb = xmb, accent = accent, height = hh,
                            modifier = Modifier
                                .graphicsLayer { translationY = ay.dp.toPx(); alpha = ao }
                                .xmbClick(src) {
                                    if (r is XmbRow.Header) return@xmbClick
                                    if (nav.picker != null) { nav.picker = null; return@xmbClick }
                                    m.selKey = r.key
                                    nav.activate(r, xmb)
                                },
                        )
                    }
                    }
                }
            }
            nav.picker?.let { p -> XmbPickerPanel(p, nav, xmb, x0 + colW, top, bottom, anchor + rowH / 2f, colW, accent) }
        }
    }
}

private val rowIconFallback: ImageVector get() = Icons.Filled.Settings

@Composable
private fun XmbStrip(icons: List<ImageVector>, sel: Int, x: Float, anchor: Float, rowH: Float, accent: Color, onTap: () -> Unit) {
    Box(Modifier.offset(x.dp, 0.dp).width(34.dp)) {
        icons.forEachIndexed { i, ic ->
            val d = i - sel
            if (abs(d) <= 4) {
            val y = anchor + (rowH - 34f) / 2f + d * 38f
            val o = if (d == 0) 1f else max(0f, 0.45f - abs(d) * 0.09f)
            val src = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .graphicsLayer { translationY = y.dp.toPx(); alpha = o }
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .then(if (d == 0) Modifier.background(accent.copy(alpha = 0.2f)).border(1.5.dp, accent.copy(alpha = 0.75f), RoundedCornerShape(10.dp)) else Modifier)
                    .xmbClick(src, onTap),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ic, null, tint = if (d == 0) Color.White else Color(0xFFCFCFCF), modifier = Modifier.size(18.dp))
            }
            }
        }
    }
}

@Composable
private fun XmbRowView(
    row: XmbRow, selected: Boolean, editing: Boolean, nav: XmbNavState, xmb: XmbScope,
    accent: Color, height: Float, modifier: Modifier,
) {
    if (row is XmbRow.Header) {
        Box(modifier.fillMaxWidth().height(height.dp).padding(start = 8.dp), contentAlignment = Alignment.CenterStart) {
            Text(row.label.uppercase(), color = accent, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        }
        return
    }
    val danger = row is XmbRow.Action && row.danger
    val tint = if (danger) DangerRed else accent
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(shape)
            .then(
                if (selected) Modifier
                    .background(Brush.horizontalGradient(listOf(tint.copy(alpha = 0.30f), tint.copy(alpha = 0.07f))))
                    .border(1.dp, tint.copy(alpha = 0.5f), shape)
                else Modifier
            )
            .padding(start = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val action = row as? XmbRow.Action
        val thumb = action?.thumbnail
        // Cover art gets a portrait tile of its own; everything else keeps the 32dp icon slot.
        val big = thumb != null && action?.thumbnailLarge == true
        val slot = if (big) Modifier.size(width = 60.dp, height = 84.dp) else Modifier.size(32.dp)
        Box(slot.clip(RoundedCornerShape(if (big) 6.dp else 8.dp)), contentAlignment = Alignment.Center) {
            if (thumb != null) Image(
                thumb, null,
                // Fit, not Crop: a cover that is not exactly 2:3 should letterbox rather than lose its edges.
                contentScale = if (big) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            else row.iconOrNull()?.let {
                Icon(it, null, modifier = Modifier.size(18.dp), tint = when {
                    selected -> Color.White
                    danger -> Color(0xFFE8A197)
                    else -> Color(0xFFCFCFCF)
                })
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                row.label,
                color = when { danger && !selected -> Color(0xFFE8A197); selected -> Color.White; else -> Color.White.copy(alpha = 0.64f) },
                fontSize = if (selected) 15.sp else 13.5.sp,
                fontWeight = if (selected) FontWeight.Normal else FontWeight.Light,
                maxLines = if (row is XmbRow.Info && row.value.isEmpty()) 2 else 1, overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp,
            )
            val sub = row.sub()
            if (selected && sub != null) {
                Text(sub, color = Color.White.copy(alpha = 0.62f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (editing && row is XmbRow.Text) {
            XmbEditField(row, nav, xmb, accent)
        } else {
            XmbValue(row, selected, accent)
        }
    }
}

@Composable
private fun XmbValue(row: XmbRow, selected: Boolean, accent: Color) {
    val vc = if (selected) Color.White else Color(0xFFBDBDBD)
    @Composable fun V(s: String) = Text(s, color = vc, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn220())
    @Composable fun Chev(s: String) = Text(s, color = vc.copy(alpha = 0.75f), fontSize = 14.sp)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when (row) {
            is XmbRow.Toggle -> {
                Box(
                    Modifier.clip(CircleShape).background(if (row.value) accent else Color(0xFF2E2E2E)).padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(if (row.value) "On" else "Off", color = if (row.value) Color.White else Color(0xFF9A9A9A), fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }
            }
            is XmbRow.Choice -> { V(row.selected); Chev("›") }
            is XmbRow.Text -> V(row.value.ifEmpty { "—" })
            is XmbRow.Slider -> { Chev("◀"); V(row.format(row.value)); Chev("▶") }
            is XmbRow.Link -> { row.value?.let { V(it) }; Chev("›") }
            is XmbRow.Action -> row.value?.let { V(it) }
            is XmbRow.Info -> Text(row.value, color = Color(0xFFE6E6E6), fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn220())
            is XmbRow.External -> { row.value?.let { V(it) }; Text("↗", color = vc.copy(alpha = 0.8f), fontSize = 12.sp) }
            is XmbRow.Header -> {}
        }
    }
}

// Caps the value column so long values ellipsize instead of squeezing the label away.
private fun Modifier.widthIn220(): Modifier = this.widthIn(max = 220.dp)

@Composable
private fun XmbEditField(row: XmbRow.Text, nav: XmbNavState, xmb: XmbScope, accent: Color) {
    val fr = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() }; keyboard?.show() }
    BasicTextField(
        value = nav.editValue,
        onValueChange = { nav.editValue = it },
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
        cursorBrush = SolidColor(accent),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (row.numeric) KeyboardType.Number else KeyboardType.Text,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); nav.commitEdit(xmb) }),
        modifier = Modifier
            .focusRequester(fr)
            .width(200.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, accent, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun XmbPickerPanel(
    p: XmbPicker, nav: XmbNavState, xmb: XmbScope,
    colRight: Float, top: Float, bottom: Float, centerY: Float, colW: Float, accent: Color,
) {
    val pw = min(340f, max(240f, colW * 0.8f))
    val px = colRight - pw
    val rowDp = 48f
    // Header + footer + rows: the card hugs its options and only scrolls when they overflow.
    val ph = min(bottom - top, 44f + 34f + 8f + p.row.options.size * rowDp)
    val py = (centerY - ph / 2f).coerceIn(top, max(top, bottom - ph))
    val shape = RoundedCornerShape(18.dp)
    val listState = rememberLazyListState()
    LaunchedEffect(p.sel) {
        val vis = listState.layoutInfo.visibleItemsInfo
        val first = vis.firstOrNull()?.index ?: return@LaunchedEffect
        val last = vis.last().index
        if (p.sel < first) listState.animateScrollToItem(p.sel)
        else if (p.sel >= last) listState.animateScrollToItem(max(0, p.sel - (last - first) + 1))
    }
    Column(
        Modifier
            .zIndex(2f)
            .offset(px.dp, py.dp)
            .width(pw.dp)
            .height(ph.dp)
            .shadow(18.dp, shape)
            .clip(shape)
            .background(Color(0xF4101015))
            .border(1.dp, accent.copy(alpha = 0.45f), shape),
    ) {
        Row(
            Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                p.row.label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Text("${p.row.options.size} options", color = Color(0xFF9A9AA5), fontSize = 12.sp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(p.row.options, key = { _, opt -> opt }) { j, opt ->
                val isSel = j == p.sel
                val isCur = opt == p.row.selected
                val off = opt in p.row.disabledOptions
                val src = remember { MutableInteractionSource() }
                val sc by animateFloatAsState(if (isSel) 1.02f else 1f, tween(180), label = "xmbPs")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(rowDp.dp)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .graphicsLayer { scaleX = sc; scaleY = sc; alpha = if (off) 0.45f else 1f }
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSel) accent.copy(alpha = 0.30f) else Color.Transparent)
                        .xmbClick(src) { p.sel = j; nav.onKey(XmbKey.A, xmb) }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.width(3.dp).height(20.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (isSel) accent else Color.Transparent),
                    )
                    Text(
                        opt,
                        color = if (isSel) Color.White else Color.White.copy(alpha = 0.78f),
                        fontSize = 15.sp,
                        fontWeight = if (isSel || isCur) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    )
                    if (off) Text("Unavailable", color = Color(0xFF9A9AA5), fontSize = 11.sp)
                    else if (isCur) Icon(Icons.Filled.Check, contentDescription = "Current", tint = accent, modifier = Modifier.size(20.dp))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
        Text(
            "A  Select     B  Back", color = Color(0xFF9A9AA5), fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().height(33.dp).wrapContentHeight(Alignment.CenterVertically).padding(horizontal = 16.dp),
        )
    }
}
