package com.winhub.hernandez.ui.screens

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.provider.Settings
import androidx.preference.PreferenceManager
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.winhub.hernandez.R
import com.winhub.hernandez.ui.Screen
import com.winhub.hernandez.store.SteamSessionManager
import com.winhub.hernandez.store.download.DownloadRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

/** Shell-wide console state: the Start-button quick menu, the idle screensaver and the idle clock. */
internal object DeckConsole {
    var quickMenu by mutableStateOf(false)
    var screensaver by mutableStateOf(false)
    var boot by mutableStateOf(false)
    private var lastInputMs = SystemClock.uptimeMillis()

    /** Any input counts as activity and wakes the screensaver. */
    fun poke() {
        lastInputMs = SystemClock.uptimeMillis()
        screensaver = false
    }

    fun idleMs(): Long = SystemClock.uptimeMillis() - lastInputMs
}

/**
 * One short sound per kind of move. "SteamOS" (the default) plays the app's own soft clips through a SoundPool, "Phone" the system's
 * click sounds, "Off" nothing; the choice is Settings > Navigation sounds. Either sound set follows the phone's touch-sounds switch.
 */
internal object DeckSound {
    const val MOVE = 0
    const val SELECT = 1
    const val BACK = 2
    const val ERROR = 3
    const val TAB = 4

    /** The preference that picks the sound set. */
    const val KEY = "nav_sounds"
    const val STEAMOS = "steamos"
    const val PHONE = "phone"
    const val OFF = "off"

    private var appContext: Context? = null
    private var audio: AudioManager? = null
    private var prefs: SharedPreferences? = null
    private var pool: SoundPool? = null
    private val ids = IntArray(5)

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        audio = app.getSystemService(AudioManager::class.java)
        prefs = PreferenceManager.getDefaultSharedPreferences(app)
        if (pool != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val sp = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build()
        ids[MOVE] = sp.load(app, R.raw.deck_snd_move, 1)
        ids[SELECT] = sp.load(app, R.raw.deck_snd_select, 1)
        ids[BACK] = sp.load(app, R.raw.deck_snd_back, 1)
        ids[ERROR] = sp.load(app, R.raw.deck_snd_error, 1)
        ids[TAB] = sp.load(app, R.raw.deck_snd_tab, 1)
        pool = sp
    }

    private fun touchSoundsOn(): Boolean {
        val app = appContext ?: return true
        return Settings.System.getInt(app.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 1) != 0
    }

    fun play(kind: Int) {
        when (prefs?.getString(KEY, STEAMOS)) {
            OFF -> return
            PHONE -> {
                val fx = when (kind) {
                    MOVE, TAB -> AudioManager.FX_FOCUS_NAVIGATION_RIGHT
                    SELECT -> AudioManager.FX_KEY_CLICK
                    BACK -> AudioManager.FX_FOCUS_NAVIGATION_LEFT
                    else -> AudioManager.FX_KEYPRESS_INVALID
                }
                audio?.playSoundEffect(fx)
            }
            else -> {
                if (!touchSoundsOn()) return
                val id = ids.getOrNull(kind) ?: return
                // A clip still loading plays nothing (id 0 from play), never an error.
                pool?.play(id, 0.8f, 0.8f, 1, 0, 1f)
            }
        }
    }
}

/** The bottom hint bar: pad glyphs with labels, and every action that has a touch equivalent is tappable. */
@Composable
internal fun DeckHintBar(
    modifier: Modifier,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    subTabs: Boolean,
) {
    Row(
        modifier = modifier.padding(end = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HintChip("A", Color(0xFF3DDC84), "Select", null)
        HintChip("B", Color(0xFFFF5252), "Back", onBack)
        HintChip("L1", Color(0xFF8A93A6), "Prev", onPrev)
        HintChip("R1", Color(0xFF8A93A6), "Next", onNext)
        if (subTabs) HintChip("L2 R2", Color(0xFF8A93A6), "Sub-tabs", null)
        HintChip("START", Color(0xFF8A93A6), "Menu") { DeckConsole.quickMenu = true }
    }
}

@Composable
private fun HintChip(glyph: String, color: Color, label: String, onClick: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.pointerInput(onClick) { detectTapGestures { onClick() } } else Modifier)
            .padding(horizontal = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .heightIn(min = 18.dp)
                .widthIn(min = 18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(color)
                .padding(horizontal = 5.dp),
        ) {
            Text(glyph, color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
        Spacer(Modifier.width(4.dp))
        Text(label, color = Deck.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Download tray, quick menu and screensaver, drawn over the whole shell. */
@Composable
internal fun DeckConsoleOverlays(onGo: (String) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        DeckSound.init(context)
        DeckConsole.poke()
        while (true) {
            delay(5_000)
            if (!DeckConsole.screensaver && !DeckConsole.quickMenu && DeckConsole.idleMs() > 120_000L) {
                DeckConsole.screensaver = true
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        DeckDownloadTray(Modifier.align(Alignment.BottomStart)) { onGo("stores") }
        if (DeckConsole.screensaver) DeckScreensaver()
    }
    if (DeckConsole.quickMenu) {
        DeckQuickMenu(onDismiss = { DeckConsole.quickMenu = false }, onGo = onGo)
    }
}

/** A persistent chip while anything downloads: progress ring, name, percent and speed. Tap opens the Stores section. */
@Composable
private fun DeckDownloadTray(modifier: Modifier, onOpen: () -> Unit) {
    val entries by DownloadRegistry.entries.collectAsState()
    val active = entries.filter { it.isActive }
    AnimatedVisibility(
        visible = active.isNotEmpty(),
        enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it },
        exit = fadeOut(tween(150)),
        modifier = modifier.padding(start = 14.dp, bottom = 10.dp),
    ) {
        val e = active.firstOrNull()
        if (e != null) {
            val shape = RoundedCornerShape(20.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(shape)
                    .background(Deck.surface.copy(alpha = 0.92f))
                    .border(1.dp, Deck.line, shape)
                    .pointerInput(onOpen) { detectTapGestures { onOpen() } }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                CircularProgressIndicator(
                    progress = e.pct.coerceIn(0, 100) / 100f,
                    color = Deck.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(24.dp),
                )
                Column {
                    Text(
                        e.name + if (active.size > 1) "  +${active.size - 1}" else "",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp),
                    )
                    Text(
                        "${e.pct}%" + if (e.speedBps > 0) "  ·  " + String.format("%.1f MB/s", e.speedBps / 1048576f) else "",
                        color = Deck.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Idle screensaver: a dimmed, slowly drifting clock. Any button or touch wakes it. */
@Composable
private fun DeckScreensaver() {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val drift by rememberInfiniteTransition(label = "saver").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(30_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "saverDrift",
    )
    val time = remember(now / 1_000L) { android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(now)) }
    val date = remember(now / 60_000L) { android.text.format.DateFormat.getMediumDateFormat(context).format(java.util.Date(now)) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            .pointerInput(Unit) { detectTapGestures { DeckConsole.poke() } },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                translationX = (drift - 0.5f) * 80.dp.toPx()
                translationY = (0.5f - drift) * 40.dp.toPx()
            },
        ) {
            Text(time, color = Color.White.copy(alpha = 0.8f), fontSize = 56.sp, fontWeight = FontWeight.Light)
            Text(date, color = Deck.onSurfaceVariant, fontSize = 16.sp)
            Text("Press any button", color = Deck.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(top = 18.dp))
        }
    }
}

/** Start-button overlay: volume, brightness, jump to Downloads/Settings. A dialog window, so the pad and touch both work. */
@Composable
private fun DeckQuickMenu(onDismiss: () -> Unit, onGo: (String) -> Unit) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableIntStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    val window = (context as? Activity)?.window
    var brightness by remember {
        mutableFloatStateOf(window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.6f)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val dialogView = LocalView.current
        Column(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .widthIn(max = 460.dp)
                .clip(DeckPalette.groupShape)
                .background(DeckPalette.surface)
                .border(1.dp, DeckPalette.line, DeckPalette.groupShape)
                // A dialog window never sees the Activity's A -> DPAD_CENTER rewrite, so activate from here.
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.ButtonA) {
                        val t = SystemClock.uptimeMillis()
                        dialogView.dispatchKeyEvent(
                            android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_CENTER, 0),
                        )
                        dialogView.dispatchKeyEvent(
                            android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_CENTER, 0),
                        )
                        true
                    } else {
                        false
                    }
                }
                .deckDialogNav(onDismiss)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Quick menu", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            QuickLevelRow(
                label = "Volume",
                icon = Icons.Filled.VolumeUp,
                level = volume / maxVolume.toFloat(),
                onMinus = {
                    volume = (volume - 1).coerceAtLeast(0)
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                },
                onPlus = {
                    volume = (volume + 1).coerceAtMost(maxVolume)
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                },
            )
            QuickLevelRow(
                label = "Brightness",
                icon = Icons.Filled.BrightnessMedium,
                level = brightness,
                onMinus = { brightness = (brightness - 0.1f).coerceAtLeast(0.05f); applyBrightness(window, brightness) },
                onPlus = { brightness = (brightness + 0.1f).coerceAtMost(1f); applyBrightness(window, brightness) },
            )
            QuickRow("Downloads", Icons.Filled.Download) { onDismiss(); onGo("stores") }
            QuickRow("Settings", Icons.Filled.Settings) { onDismiss(); onGo("settings") }
            QuickRow("Close", Icons.Filled.Close, onDismiss)
            Text("B  Back", color = Deck.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

private fun applyBrightness(window: android.view.Window?, value: Float) {
    window ?: return
    window.attributes = window.attributes.also { it.screenBrightness = value }
}

@Composable
private fun QuickRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(Deck.surfaceVariant)
            .deckFocusBorder(src, shape)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Deck.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun QuickLevelRow(label: String, icon: ImageVector, level: Float, onMinus: () -> Unit, onPlus: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(Deck.surfaceVariant)
            .padding(horizontal = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Deck.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(96.dp))
        QuickStep("-", onMinus)
        LinearProgressIndicator(
            progress = level.coerceIn(0f, 1f),
            color = Deck.primary,
            trackColor = Deck.line,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        QuickStep("+", onPlus)
    }
}

@Composable
private fun QuickStep(symbol: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .deckFocusBorder(src, CircleShape)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
    ) {
        Text(symbol, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

/** The launch screen shows once per app start, not every time the Deck shell is recomposed. */
internal object DeckBoot {
    var shown = false
}

/**
 * Launch screen: the app mark over the Deck glow while the store services come up - Steam's session is
 * really connected (bounded wait), the other stores report their saved sign-in. Tap or A skips it; it
 * ends by itself once every store has answered, and never shows again until the app is restarted.
 */
@Composable
internal fun DeckBootScreen(onDone: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    remember { DeckConsole.boot = true }
    val names = remember { listOf("Steam", "Epic", "GOG", "Amazon", "Xbox") }
    // 0 waiting, 1 working, 2 ready, 3 needs attention
    val status = remember { mutableStateMapOf<String, Pair<Int, String>>() }
    LaunchedEffect(Unit) {
        snapshotFlow { DeckConsole.boot }.first { !it }
        DeckBoot.shown = true
        onDone()
    }
    LaunchedEffect(Unit) {
        val started = SystemClock.uptimeMillis()
        names.forEach { status[it] = 0 to "Waiting" }
        coroutineScope {
            launch {
                status["Steam"] = 1 to "Connecting"
                val result = withContext(Dispatchers.IO) { SteamSessionManager.ensureSession(appContext, 12_000L) }
                status["Steam"] = when (result) {
                    is SteamSessionManager.SessionResult.Ok -> 2 to "Online"
                    is SteamSessionManager.SessionResult.NeedsSignIn -> 3 to "Sign in needed"
                    is SteamSessionManager.SessionResult.Offline -> 3 to "Offline"
                }
            }
            launch {
                withContext(Dispatchers.IO) { DownloadRegistry.init(appContext) }
                listOf("Epic", "GOG", "Amazon", "Xbox").forEach { name ->
                    status[name] = 1 to "Checking"
                    delay(220)
                    val signedIn = withContext(Dispatchers.IO) {
                        deckSignedIn(
                            appContext,
                            when (name) {
                                "Epic" -> Screen.Epic
                                "GOG" -> Screen.Gog
                                "Xbox" -> Screen.Xbox
                                else -> Screen.Amazon
                            },
                        )
                    }
                    status[name] = if (signedIn) 2 to "Signed in" else 3 to "Not signed in"
                }
            }
        }
        val rest = 1_400L - (SystemClock.uptimeMillis() - started)
        if (rest > 0) delay(rest)
        DeckConsole.boot = false
    }
    val pulse by rememberInfiniteTransition(label = "boot").animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1_400), RepeatMode.Reverse),
        label = "bootPulse",
    )
    val accent = Deck.primary
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Deck.background)
            .drawBehind {
                val c = Offset(size.width * 0.5f, size.height * 0.38f)
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.28f), Color.Transparent), c, size.width * 0.6f), size.width * 0.6f, c)
            }
            .pointerInput(Unit) { detectTapGestures { DeckConsole.boot = false } },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Image(
                painter = painterResource(R.drawable.deck_boot_logo),
                contentDescription = "WinHub",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(120.dp).graphicsLayer { scaleX = pulse; scaleY = pulse },
            )
            Text("WinHub", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text("Starting services", color = Deck.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))
            names.forEach { name ->
                val (state, detail) = status[name] ?: (0 to "Waiting")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.width(230.dp).height(26.dp),
                ) {
                    Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                        if (state == 1) {
                            CircularProgressIndicator(color = accent, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                        } else {
                            Box(
                                Modifier.size(8.dp).clip(CircleShape).background(
                                    when (state) {
                                        2 -> Color(0xFF3DDC84)
                                        3 -> Deck.attention
                                        else -> Deck.line
                                    },
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(72.dp))
                    Text(detail, color = Deck.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
        Text(
            "A / tap to skip",
            color = Deck.onSurfaceVariant.copy(alpha = 0.6f),
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
        )
    }
}
