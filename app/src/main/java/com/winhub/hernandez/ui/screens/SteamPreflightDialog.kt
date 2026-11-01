package com.winhub.hernandez.ui.screens

import android.animation.ValueAnimator
import android.content.res.Configuration
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.winhub.hernandez.R
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.store.SteamGameUpdater
import com.winhub.hernandez.store.SteamLiteComponent
import com.winhub.hernandez.store.SteamSessionManager
import com.winhub.hernandez.store.SteamSessionManager.Step
import com.winhub.hernandez.store.SteamSessionManager.StepState
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay

// Plain-terms help copy for the "?" bubbles (same idea as LaunchMethodSheet's: what it is, one breath).
private const val HELP_PREFLIGHT =
    "Five quick checks before the game opens, so it starts with a working Steam login, your latest " +
        "saves, a known network shape, the current build and an up-to-date SteamLite client. Cancel " +
        "stops it at any point."
private const val HELP_SESSION =
    "Makes sure you're signed in to Steam (and refreshes your login if it's about to expire) before " +
        "the game starts."
private const val HELP_CLOUD =
    "Pulls your newest cloud saves into the game folder first so you don't play on an old save. " +
        "A newer local save is never overwritten."
private const val HELP_NETWORK =
    "How this network handles online game traffic. Steam sign-in and downloads work on any NAT; " +
        "games with their own servers (e.g. Brawlhalla) can fail behind a strict/symmetric NAT such " +
        "as phone hotspots or many VPNs. Fix: home Wi-Fi, a hotspot with port mapping, or a VPN with " +
        "port forwarding."
private const val HELP_UPDATE =
    "Checks whether Steam has a newer build of the game; you choose whether to update before playing."
private const val HELP_NEED_SIGN_IN =
    "Sign in — open the Steam login, then launch again.\n" +
        "Launch with Goldberg — play offline with the stand-in Steam (no online play, no VAC).\n" +
        "Cancel — go back without launching."
private const val HELP_OFFLINE =
    "Retry — try to reach Steam again.\n" +
        "Launch anyway — start the game and let its own Steam client try to sign in.\n" +
        "Goldberg — play offline with the stand-in Steam (no online play, no VAC).\n" +
        "Cancel — go back without launching."
private const val HELP_UPDATE_OFFER =
    "Update — download the newer build now; launch again when it's done.\n" +
        "Launch anyway — play the build you have (real Steam may refuse it online).\n" +
        "Cancel — go back without launching."
private const val HELP_CLIENT =
    "WinHub's small Steam client for online (VAC) launches. Newer versions add features the app " +
        "relies on (live launch status, in-game friends). Update downloads ~18 MB and re-stages it " +
        "into your container."
private const val HELP_CLIENT_OFFER =
    "Update — download the newest SteamLite package, then launch.\n" +
        "Launch anyway — play with the installed SteamLite; newer app features stay off.\n" +
        "Cancel — go back without launching."

// ── Slideshow model ──────────────────────────────────────────────────────────────────────────────

/** The four bundled photos, one per phase (same for every game; drawable-nodpi WebP, ≤1024 px). */
private val SLIDE_PHOTOS = intArrayOf(
    R.drawable.preflight_1_connecting,
    R.drawable.preflight_2_signed_in,
    R.drawable.preflight_3_cloud,
    R.drawable.preflight_4_final,
)

/** Which slide a step's live text captions: sign-in → 1, network → 2 (over the "signed in" photo),
 *  cloud → 3, game files + SteamLite client + "Launching…" → 4. Mirrors the pre-flight's order. */
private fun slideOf(step: Step): Int = when (step) {
    Step.SESSION -> 0
    Step.NETWORK -> 1
    Step.CLOUD -> 2
    Step.UPDATE, Step.CLIENT -> 3
}

/** Every slide stays up at least this long, even when its step finished instantly (a skip). */
private const val SLIDE_HOLD_MS = 1300L
private const val CROSSFADE_MS = 450
private const val KEN_BURNS_MS = 6000

private enum class Tone { NEUTRAL, OK, AMBER, RED }
private enum class Trail { SPINNER, CHECK, NONE }

/** What the caption block over a slide shows: small step label, live line, its colour + trailing glyph,
 *  and which help text the caption's "?" opens. */
private data class Caption(val label: String, val line: String, val tone: Tone, val trail: Trail, val help: String)

private fun toneOf(state: StepState?): Tone = when (state) {
    StepState.DONE -> Tone.OK
    StepState.NOTICE -> Tone.AMBER
    StepState.WARN -> Tone.RED
    else -> Tone.NEUTRAL
}

private fun trailOf(state: StepState?): Trail = when (state) {
    StepState.RUNNING, StepState.PENDING, null -> Trail.SPINNER
    StepState.DONE, StepState.NOTICE, StepState.SKIPPED -> Trail.CHECK
    StepState.WARN -> Trail.NONE
}

/**
 * The SteamLite launch pre-flight ("Getting Steam ready") — a small modal that runs
 * [SteamSessionManager.preflightAsync] for [shortcut] BEFORE the container opens: Steam sign-in →
 * network shape (NAT verdict, informational) → cloud saves → update check → SteamLite client check.
 * The body is a four-photo slideshow whose caption is the live step text (one photo per phase,
 * each held ≥ [SLIDE_HOLD_MS]); a failure freezes the show on that phase's photo and puts the fail
 * card under it (portrait) or beside it (landscape). Cancel always works.
 *
 * Outcomes:
 *  - all green → [onLaunch] (the caller starts `XServerDisplayActivity` with `preflightDone=1`);
 *  - unusable sign-in → "Steam isn't ready" with **Sign in** / **Launch with Goldberg (offline)** /
 *    **Cancel** ([onSignIn] / [onGoldberg] / [onDismiss]);
 *  - no session in time → **Retry** / **Launch anyway** / **Launch with Goldberg** / **Cancel**;
 *  - update available → **Update** ([onUpdate], the existing manual Update pass) / **Launch anyway**;
 *  - newer SteamLite package → **Update** (downloads on the last slide, then [onLaunch]) / **Launch
 *    anyway** (hidden when the installed package predates [SteamLiteComponent.MIN_AGENT_VERSION]).
 *    A failed download never blocks: a toast says so and the installed package launches.
 *
 * Non-Steam / Goldberg / Raw launches never open this dialog — only the RealSteam pick and
 * remembered RealSteam launches route through it.
 */
@Composable
fun SteamPreflightDialog(
    shortcut: Shortcut,
    request: SteamSessionManager.PreflightRequest,
    onLaunch: () -> Unit,
    onDismiss: () -> Unit,
    onSignIn: () -> Unit,
    onGoldberg: () -> Unit,
    onUpdate: (SteamGameUpdater.UpdateStatus) -> Unit,
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val states = remember(shortcut) { mutableStateMapOf<Step, Pair<StepState, String>>() }
    // Terminal outcome awaiting a user choice (null = still running / launching).
    var needSignIn by remember(shortcut) { mutableStateOf<String?>(null) }
    var offline by remember(shortcut) { mutableStateOf<String?>(null) }
    var updateOffer by remember(shortcut) { mutableStateOf<SteamGameUpdater.UpdateStatus?>(null) }
    // SteamLite package offer (terminal — every other step is done) + its on-slide download.
    var clientOffer by remember(shortcut) { mutableStateOf<SteamLiteComponent.UpdateCheck?>(null) }
    var clientUpdating by remember(shortcut) { mutableStateOf(false) }
    var clientProgress by remember(shortcut) { mutableStateOf(0f) }
    var launching by remember(shortcut) { mutableStateOf(false) }
    // Cleared when the dialog goes away so a download that finishes after Cancel never launches.
    val alive = remember(shortcut) { AtomicBoolean(true) }
    DisposableEffect(shortcut) { onDispose { alive.set(false) } }
    // Re-run generation: bumps when the user picks Retry / Launch anyway so a fresh pass starts.
    var run by remember(shortcut) { mutableStateOf(0) }
    var skipSession by remember(shortcut) { mutableStateOf(false) }
    var skipUpdate by remember(shortcut) { mutableStateOf(false) }
    // The active "?" help bubble (null = none). Tapping the same dot again closes it.
    var helpText by remember(shortcut) { mutableStateOf<String?>(null) }
    val toggleHelp: (String) -> Unit = { helpText = if (helpText == it) null else it }

    DisposableEffect(shortcut, run) {
        needSignIn = null; offline = null; updateOffer = null; clientOffer = null
        states.clear()   // a re-run replays the show from slide 1; stale steps would jump it ahead
        val handle = SteamSessionManager.preflightAsync(
            context, request,
            object : SteamSessionManager.PreflightListener {
                override fun onStep(step: Step, state: StepState, text: String) { states[step] = state to text }
                override fun onNeedSignIn(reason: String) { needSignIn = reason }
                override fun onOffline(reason: String) { offline = reason }
                override fun onUpdateAvailable(status: SteamGameUpdater.UpdateStatus) { updateOffer = status }
                override fun onClientUpdateAvailable(check: SteamLiteComponent.UpdateCheck) { clientOffer = check }
                override fun onReady() { launching = true; onLaunch() }
                override fun onCancelled() {}
            },
            skipSession = skipSession,
            skipUpdate = skipUpdate,
        )
        onDispose { handle.cancel() }
    }

    // Warm Coil's memory cache with all four photos up front so every crossfade lands on a decoded
    // bitmap (the first slide is on screen within a frame or two either way).
    LaunchedEffect(Unit) {
        val loader = context.imageLoader
        SLIDE_PHOTOS.forEach { loader.enqueue(ImageRequest.Builder(context).data(it).build()) }
    }

    // "Update" on the SteamLite offer: download on the last slide, then launch. A failure is one
    // toast and the launch goes ahead on the installed package (the pre-flight never blocks on it).
    val updateClientThenLaunch: () -> Unit = {
        val offer = clientOffer
        if (offer != null && !clientUpdating) {
            clientUpdating = true
            clientProgress = 0f
            states[Step.CLIENT] = StepState.RUNNING to "Downloading SteamLite v${offer.latestVersion}…"
            SteamLiteComponent.downloadAsync(
                context,
                { f -> clientProgress = f },
                { ok, _ ->
                    if (alive.get()) {
                        clientUpdating = false
                        if (ok) {
                            states[Step.CLIENT] = StepState.DONE to "Updated to v${offer.latestVersion}"
                        } else {
                            val installed = SteamLiteComponent.versionLabel(offer.installed)
                            states[Step.CLIENT] = StepState.WARN to "Update failed — launching installed $installed"
                            Toast.makeText(context, "SteamLite update failed — launching installed $installed", Toast.LENGTH_LONG).show()
                        }
                        clientOffer = null
                        launching = true
                        onLaunch()
                    }
                },
            )
        }
    }

    // ── Slide scheduling: the show follows the newest reported step, one slide at a time, and
    // never leaves a slide before SLIDE_HOLD_MS — so a skipped step still shows its photo with the
    // skip text, and a slow step simply keeps its slide with the spinner. ─────────────────────────
    val desired = if (launching) 3 else (states.keys.maxOfOrNull { slideOf(it) } ?: 0)
    var shown by remember(shortcut, run) { mutableStateOf(0) }
    var shownSince by remember(shortcut, run) { mutableStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(desired, shown) {
        if (shown < desired) {
            val wait = SLIDE_HOLD_MS - (SystemClock.uptimeMillis() - shownSince)
            if (wait > 0) delay(wait)
            shown += 1
            shownSince = SystemClock.uptimeMillis()
        }
    }
    // Reduced motion: the system "Remove animations" / animator scale 0 → no zoom, instant swaps.
    val motion = remember { ValueAnimator.areAnimatorsEnabled() }
    val caption = captionFor(shown, states, launching, shortcut.name)

    val fail = failCardFor(
        shortcut, needSignIn, offline, updateOffer, clientOffer, clientUpdating,
        onSignIn = onSignIn, onGoldberg = onGoldberg,
        retry = { skipSession = false; run++ },
        launchAnyway = { skipSession = true; run++ },
        launchThisBuild = { skipUpdate = true; skipSession = true; run++ },
        onUpdate = { updateOffer?.let(onUpdate) },
        launchInstalledClient = { launching = true; onLaunch() },
        updateClient = updateClientThenLaunch,
    )
    // The title's "?" explains whatever the fail card currently offers (or the pre-flight as a
    // whole while it runs) — one dot instead of one per TextButton, which wouldn't fit.
    val titleHelp = when {
        needSignIn != null -> HELP_NEED_SIGN_IN
        offline != null -> HELP_OFFLINE
        updateOffer != null -> HELP_UPDATE_OFFER
        clientOffer != null -> HELP_CLIENT_OFFER
        else -> HELP_PREFLIGHT
    }
    val title = if (launching) "Launching ${shortcut.name}…" else "Getting Steam ready"
    val show = SlideShow(
        shown = shown,
        caption = caption,
        motion = motion,
        progress = if (clientUpdating) clientProgress else null,
        helpOpen = helpText == caption.help,
        onHelp = { toggleHelp(caption.help) },
    )

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (landscape) {
        LandscapePreflight(
            title, titleHelp, shortcut.name, show, fail, helpText, toggleHelp, { helpText = null }, onDismiss,
        )
    } else {
        OutlinedAlertDialog(
            onDismissRequest = { /* modal — Cancel / a choice closes it */ },
            containerColor = cs.surfaceContainerHigh,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
            title = { TitleRow(title, highlighted = helpText == titleHelp) { toggleHelp(titleHelp) } },
            text = {
                Column {
                    Text(
                        shortcut.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(10.dp))
                    // Photo strip: height follows the dialog width (16:8.2, as the mock), capped so
                    // a wide tablet dialog never grows past the old five-row list.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        SlideStrip(
                            show,
                            photoBias = -0.4f,
                            modifier = Modifier.heightIn(max = 240.dp).aspectRatio(16f / 8.2f),
                        )
                    }
                    helpText?.let { tip ->
                        Spacer(Modifier.height(8.dp))
                        PreflightHelpTip(tip) { helpText = null }
                    }
                    fail?.let {
                        Spacer(Modifier.height(10.dp))
                        FailCard(it)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel", color = cs.onSurfaceVariant) }
            },
        )
    }
}

// ── Caption / fail-card derivation ───────────────────────────────────────────────────────────────

/** The caption for slide [slide] from the live step map (each slide reads only its own steps, so a
 *  slide held past its step's completion keeps showing that step's final text + ✓). */
private fun captionFor(
    slide: Int,
    states: Map<Step, Pair<StepState, String>>,
    launching: Boolean,
    gameName: String,
): Caption {
    fun of(label: String, step: Step, help: String, pendingLine: String): Caption {
        val st = states[step]
        return Caption(label, st?.second ?: pendingLine, toneOf(st?.first), trailOf(st?.first), help)
    }
    return when (slide) {
        0 -> of("Steam sign-in", Step.SESSION, HELP_SESSION, "Checking Steam sign-in…")
        1 -> {
            // "Signed in to Steam" only when that's true; a Launch-anyway pass (session skipped)
            // keeps the plain step name so the photo isn't captioned with a claim it can't make.
            val signedIn = states[Step.SESSION]?.first == StepState.DONE
            of(if (signedIn) "Signed in to Steam" else "Network", Step.NETWORK, HELP_NETWORK, "Checking network…")
        }
        2 -> of("Cloud saves", Step.CLOUD, HELP_CLOUD, "Syncing cloud saves…")
        else -> when {
            launching -> Caption("Ready", "Launching $gameName…", Tone.OK, Trail.CHECK, HELP_PREFLIGHT)
            states.containsKey(Step.CLIENT) -> of("SteamLite client", Step.CLIENT, HELP_CLIENT, "Checking for SteamLite updates…")
            else -> of("Game files", Step.UPDATE, HELP_UPDATE, "Checking for updates…")
        }
    }
}

/** A terminal outcome's card: title, plain-terms body and its choice buttons (Cancel stays in the
 *  footer). Red for "can't launch online as-is", accent for the two update offers. */
private data class FailCardModel(
    val title: String,
    val body: String,
    val red: Boolean,
    val buttons: @Composable () -> Unit,
)

@Composable
private fun failCardFor(
    shortcut: Shortcut,
    needSignIn: String?,
    offline: String?,
    updateOffer: SteamGameUpdater.UpdateStatus?,
    clientOffer: SteamLiteComponent.UpdateCheck?,
    clientUpdating: Boolean,
    onSignIn: () -> Unit,
    onGoldberg: () -> Unit,
    retry: () -> Unit,
    launchAnyway: () -> Unit,
    launchThisBuild: () -> Unit,
    onUpdate: () -> Unit,
    launchInstalledClient: () -> Unit,
    updateClient: () -> Unit,
): FailCardModel? {
    val cs = MaterialTheme.colorScheme
    return when {
        needSignIn != null -> FailCardModel(
            "Steam isn't ready",
            "$needSignIn. Sign in again to play online with SteamLite, or launch offline with Goldberg.",
            red = true,
        ) {
            TextButton(onClick = onGoldberg) { Text("Launch with Goldberg", color = cs.onSurfaceVariant) }
            TextButton(onClick = onSignIn) { Text("Sign in", color = cs.primary) }
        }
        offline != null -> FailCardModel(
            "Steam is unreachable",
            "$offline. You can retry, launch anyway (the game's own Steam client will try to sign in), or launch offline with Goldberg.",
            red = true,
        ) {
            TextButton(onClick = onGoldberg) { Text("Goldberg", color = cs.onSurfaceVariant) }
            TextButton(onClick = launchAnyway) { Text("Launch anyway", color = cs.onSurfaceVariant) }
            TextButton(onClick = retry) { Text("Retry", color = cs.primary) }
        }
        updateOffer != null -> FailCardModel(
            "Update available",
            if (updateOffer.installedBuild > 0L && updateOffer.liveBuild > 0L)
                "Build ${updateOffer.installedBuild} → ${updateOffer.liveBuild}. Real Steam may refuse an out-of-date build online."
            else "A newer build of ${shortcut.name} is available.",
            red = false,
        ) {
            TextButton(onClick = launchThisBuild) { Text("Launch anyway", color = cs.onSurfaceVariant) }
            TextButton(onClick = onUpdate) { Text("Update", color = cs.primary) }
        }
        clientOffer != null && !clientUpdating -> {
            val size = if (clientOffer.downloadMb > 0) " (~${clientOffer.downloadMb} MB)" else ""
            val installed = SteamLiteComponent.versionLabel(clientOffer.installed)
            FailCardModel(
                if (clientOffer.required) "SteamLite update required" else "SteamLite update available",
                if (clientOffer.required)
                    "This version of WinHub needs SteamLite v${clientOffer.latestVersion} for live launch " +
                        "status and in-game friends. Update now$size; if the download fails, the " +
                        "installed $installed launches instead."
                else
                    "A newer SteamLite (v${clientOffer.latestVersion}) is available$size. Update now, or launch " +
                        "with the installed $installed.",
                red = false,
            ) {
                // Below MIN_AGENT_VERSION the only offered action is Update (a failed download
                // still launches the installed package — see updateClientThenLaunch).
                if (!clientOffer.required) {
                    TextButton(onClick = launchInstalledClient) { Text("Launch anyway", color = cs.onSurfaceVariant) }
                }
                TextButton(onClick = updateClient) { Text("Update", color = cs.primary) }
            }
        }
        else -> null
    }
}

// ── Landscape: photo column left, text + fail card right (LaunchMethodSheet's cover-art hero shape) ─

/** Everything the slide strip needs to draw itself, orientation-agnostic. */
private data class SlideShow(
    val shown: Int,
    val caption: Caption,
    val motion: Boolean,
    val progress: Float?,      // non-null = determinate bar under the caption (SteamLite download)
    val helpOpen: Boolean,
    val onHelp: () -> Unit,
)

@Composable
private fun LandscapePreflight(
    title: String,
    titleHelp: String,
    gameName: String,
    show: SlideShow,
    fail: FailCardModel?,
    helpText: String?,
    toggleHelp: (String) -> Unit,
    closeHelp: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val cfg = LocalConfiguration.current
    // Fit-to-screen: the card never exceeds 92 % × 86 % of the landscape screen (dp), and the photo
    // column is sized from the card HEIGHT (near-square, ≤ 52 % of the width) so a short, wide
    // handheld at a high font scale can't push anything off the card. Below ~300 dp of usable
    // height there's no room for a full-height photo: the show collapses to a strip above the text.
    val maxW = cfg.screenWidthDp * 0.92f
    val maxH = cfg.screenHeightDp * 0.86f
    val compact = maxH < 300f
    val cardW = minOf(maxW, if (compact) 640f else 760f).coerceAtLeast(300f).dp
    val cardH = minOf(maxH, 380f).coerceAtLeast(200f).dp
    val photoW = minOf(cardH.value * 1.08f, cardW.value * 0.52f).dp
    val shape = RoundedCornerShape(28.dp)
    Dialog(
        onDismissRequest = { /* modal — Cancel / a choice closes it */ },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.width(cardW).height(cardH),
            shape = shape,
            color = cs.surfaceContainerHigh,
            contentColor = cs.onSurface,
            border = BorderStroke(1.dp, cs.outline),
        ) {
            if (compact) {
                Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp)) {
                    TitleRow(title, highlighted = helpText == titleHelp) { toggleHelp(titleHelp) }
                    Spacer(Modifier.height(6.dp))
                    SlideStrip(
                        show, photoBias = -0.5f,
                        modifier = Modifier.fillMaxWidth().height((cardH.value * 0.42f).dp),
                    )
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        Spacer(Modifier.height(6.dp))
                        LandscapeText(gameName, show.caption, fail, helpText, closeHelp, summary = false)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Cancel", color = cs.onSurfaceVariant) }
                    }
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    SlideStrip(
                        show, photoBias = -0.5f,
                        modifier = Modifier.fillMaxHeight().width(photoW),
                        rounded = false,
                        captionBottom = 22.dp,
                    )
                    Column(Modifier.weight(1f).fillMaxHeight().padding(start = 18.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)) {
                        TitleRow(title, highlighted = helpText == titleHelp) { toggleHelp(titleHelp) }
                        // Whatever can't fit the fixed-height card scrolls; Cancel below stays pinned.
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                            Spacer(Modifier.height(4.dp))
                            LandscapeText(gameName, show.caption, fail, helpText, closeHelp, summary = true)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onDismiss) { Text("Cancel", color = cs.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}

/** The right column's text: game name, one-line summary, the current step echoed in its colour,
 *  then the open help tip and the fail card (if any). */
@Composable
private fun LandscapeText(
    gameName: String,
    caption: Caption,
    fail: FailCardModel?,
    helpText: String?,
    closeHelp: () -> Unit,
    summary: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    Text(gameName, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
    if (summary) {
        Spacer(Modifier.height(4.dp))
        Text("Five quick checks before the game starts.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "${caption.label} — ${caption.line}",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = toneColor(caption.tone, cs.onSurface),
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
    helpText?.let { tip ->
        Spacer(Modifier.height(8.dp))
        PreflightHelpTip(tip, closeHelp)
    }
    fail?.let {
        Spacer(Modifier.height(10.dp))
        FailCard(it)
    }
}

// ── The slide strip: crossfading photo + Ken-Burns zoom, gradient, caption, dots ─────────────────

@Composable
private fun SlideStrip(
    show: SlideShow,
    photoBias: Float,            // vertical crop bias: subjects sit in the upper third of every photo
    modifier: Modifier,
    rounded: Boolean = true,
    captionBottom: Dp = 16.dp,
) {
    val cs = MaterialTheme.colorScheme
    val base = if (rounded) modifier.clip(RoundedCornerShape(14.dp)) else modifier
    Box(base.background(Color.Black)) {
        Crossfade(
            targetState = show.shown,
            animationSpec = if (show.motion) tween(CROSSFADE_MS) else snap(),
            label = "preflight-slide",
        ) { idx ->
            SlidePhoto(SLIDE_PHOTOS[idx], show.motion, photoBias)
        }
        // Bottom fade so the caption stays legible over any photo.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.62f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEB140C08)))),
        )
        val c = show.caption
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = captionBottom),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        c.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.12.em,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        c.line,
                        style = MaterialTheme.typography.titleSmall,
                        color = toneColor(c.tone, Color.White),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    when (c.trail) {
                        Trail.SPINNER -> CircularProgressIndicator(
                            modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp,
                            color = cs.primary, trackColor = Color.White.copy(alpha = 0.25f),
                        )
                        Trail.CHECK -> Box(
                            Modifier.size(20.dp).clip(CircleShape)
                                .background(if (c.tone == Tone.NEUTRAL) Color.White.copy(alpha = 0.35f) else toneColor(c.tone, OkGreen)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF0B2A17), modifier = Modifier.size(14.dp))
                        }
                        Trail.NONE -> {}
                    }
                }
                Spacer(Modifier.width(8.dp))
                PhotoHelpDot(highlighted = show.helpOpen, onClick = show.onHelp)
            }
            show.progress?.let { p ->
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { p.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = cs.primary,
                    trackColor = Color.White.copy(alpha = 0.25f),
                )
            }
        }
        // Four dot indicators in the photo's bottom margin: active = pill, done = muted, rest = faint.
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in SLIDE_PHOTOS.indices) {
                val w by animateDpAsState(
                    targetValue = if (i == show.shown) 18.dp else 6.dp,
                    animationSpec = if (show.motion) tween(300) else snap(),
                    label = "preflight-dot",
                )
                Box(
                    Modifier.height(6.dp).width(w).clip(RoundedCornerShape(3.dp)).background(
                        when {
                            i == show.shown -> cs.primary
                            i < show.shown -> Color.White.copy(alpha = 0.55f)
                            else -> Color.White.copy(alpha = 0.22f)
                        },
                    ),
                )
            }
        }
    }
}

/** One photo, cropped with the subject kept high, drifting from 1.02× to 1.08× over the slide. */
@Composable
private fun SlidePhoto(res: Int, motion: Boolean, bias: Float) {
    val scale = remember { Animatable(if (motion) 1.02f else 1f) }
    LaunchedEffect(Unit) {
        if (motion) scale.animateTo(1.08f, tween(KEN_BURNS_MS, easing = LinearEasing))
    }
    AsyncImage(
        model = res,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = BiasAlignment(0f, bias),
        modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale.value; scaleY = scale.value },
    )
}

/** The mock's green for a finished step (over the photo and in the landscape echo). */
private val OkGreen = Color(0xFF3AD07A)

/** The app's amber (PreloaderOverlay's mid Metacritic tier) for the informational NOTICE state. */
private val NoticeAmber = Color(0xFFE1A100)

@Composable
private fun toneColor(tone: Tone, neutral: Color): Color = when (tone) {
    Tone.OK -> OkGreen
    Tone.AMBER -> NoticeAmber
    Tone.RED -> MaterialTheme.colorScheme.error
    Tone.NEUTRAL -> neutral
}

// ── Shared chrome: title row, fail card, help dots + tip ─────────────────────────────────────────

@Composable
private fun TitleRow(title: String, highlighted: Boolean, onHelp: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = cs.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        PreflightHelpDot(highlighted = highlighted, onClick = onHelp)
    }
}

/** The terminal-outcome card: title, body, and its choices wrapping onto more lines when narrow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FailCard(card: FailCardModel) {
    val cs = MaterialTheme.colorScheme
    val edge = if (card.red) cs.error else cs.primary
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(edge.copy(alpha = 0.10f))
            .border(1.dp, edge.copy(alpha = 0.45f), shape)
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
    ) {
        Text(card.title, style = MaterialTheme.typography.titleSmall, color = cs.onSurface, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(card.body, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { card.buttons() }
    }
}

/** The launch popup's corner "?" (LaunchMethodSheet.HelpDot), on the dialog's primary accent. */
@Composable
private fun PreflightHelpDot(highlighted: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(16.dp).clip(CircleShape)
            .background(if (highlighted) cs.primary.copy(alpha = 0.16f) else cs.surfaceVariant)
            .border(1.dp, if (highlighted) cs.primary.copy(alpha = 0.55f) else cs.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "?",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = if (highlighted) cs.primary else cs.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** The same "?" drawn for the photo caption: a light outline that reads over any picture. */
@Composable
private fun PhotoHelpDot(highlighted: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(20.dp).clip(CircleShape)
            .background(if (highlighted) cs.primary.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.25f))
            .border(1.dp, if (highlighted) cs.primary else Color.White.copy(alpha = 0.45f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "?",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            color = if (highlighted) Color.White else Color.White.copy(alpha = 0.8f),
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Inline counterpart of the launch popup's floating help tip — sits under the photo; tap to dismiss. */
@Composable
private fun PreflightHelpTip(text: String, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cs.surfaceVariant)
            .border(1.dp, cs.primary.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = cs.onSurface)
    }
}
