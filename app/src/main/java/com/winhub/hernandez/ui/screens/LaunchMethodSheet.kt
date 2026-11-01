package com.winhub.hernandez.ui.screens

import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import com.winhub.hernandez.ui.components.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.winhub.hernandez.ui.components.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.store.GoldbergMode
import com.winhub.hernandez.store.SteamGameDetailActivity
import com.winhub.hernandez.store.SteamLiteComponent
import com.winhub.hernandez.store.SteamPrefs
import com.winhub.hernandez.store.SteamRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

// ── Where a shortcut comes from (drives which launch methods the popup offers) ────────────────────
private enum class GameSource { STEAM, EPIC, GOG, AMAZON, XBOX, CUSTOM }

/**
 * The SteamLite package status shown under the SteamLite chip + the footer's "Update & Launch" gate.
 * Present only for Steam games with the package already installed (a missing package is fetched by
 * the launch itself). [check] null = the bounded catalog check is still running; [note] = the
 * one-line failure notice after a download that didn't make it.
 */
private class SteamLiteClientUi(
    val check: SteamLiteComponent.UpdateCheck?,
    val updating: Boolean,
    val progress: Float,
    val note: String?,
    val onUpdateAndLaunch: () -> Unit,
) {
    /** A newer package is on offer and nothing has gone wrong yet → the footer gates on it. */
    val gated: Boolean get() = check?.available == true && note == null
}

// The launch methods. STEAMLITE → "RealSteam", GOLDBERG → "Goldberg", RAW → "Raw" (the launchMode
// contract literals the launch pipeline + callers already understand).
private enum class LaunchMethod(val mode: String) { STEAMLITE("RealSteam"), GOLDBERG("Goldberg"), RAW("Raw"), STREAM("Stream") }

/**
 * Classify a shortcut's store source. Mirrors ShortcutsScreen's per-source gates so this popup offers
 * exactly the methods a source supports: Steam ([isSteamOriginShortcut]) gets all three, everything else
 * is Raw-only. GOG shortcuts are written UNTAGGED, so the load-bearing GOG signal is the `gog_games`
 * exec path (the `storeSource==gog` branch is forward-compat) — same rule as [isGogShortcut].
 */
private fun classifySource(shortcut: Shortcut): GameSource = when {
    isSteamOriginShortcut(shortcut) -> GameSource.STEAM
    shortcut.getExtra("storeSource") == "epic" -> GameSource.EPIC
    shortcut.getExtra("storeSource") == "gog" ||
        (shortcut.path?.contains("gog_games", ignoreCase = true) == true) -> GameSource.GOG
    isAmazonShortcut(shortcut) -> GameSource.AMAZON
    isXboxShortcut(shortcut) -> GameSource.XBOX
    else -> GameSource.CUSTOM
}

// Plain-terms help copy for the "?" bubbles (kept short: what it is + when to use it).
private const val HELP_LAUNCH_WITH =
    "How the game talks to Steam. SteamLite = real Steam (online, VAC, real achievements). Goldberg = " +
        "fake offline Steam for single-player. Raw = just run the .exe."
private const val HELP_STEAMLITE =
    "SteamLite — the REAL Steam client, signed into your account. Online on VAC servers, real " +
        "achievements & cloud saves. Needs internet + a game you own on Steam."
private const val HELP_GOLDBERG =
    "Goldberg — a stand-in, offline Steam. No login, lightweight; great for single-player. Achievements " +
        "emulated on-device. No online multiplayer or VAC. Steam-library games only."
private const val HELP_RAW =
    "Raw — launch the game's .exe directly, with no Steam layer. For DRM-free games or when you just " +
        "want the game to start."
private const val HELP_STREAM =
    "Stream — plays the game on Microsoft's servers with Xbox Cloud Gaming, nothing runs on this device. " +
        "Needs a steady connection and an Xbox plan that includes cloud play."
private const val HELP_PASS =
    "For classic games (Half-Life 2, CS:S) that ignore a controller in Real-Steam mode. Hands the pad " +
        "straight to the game instead of Steam Input. SteamLite only."
private const val HELP_VAC =
    "On: the game must be started by Steam itself (VAC-secure). If Steam can't, you get a warning and " +
        "up to ~60 s of waiting before a direct start. Off: the game has no VAC, so a direct start after " +
        "~15 s is fine. Auto-detected from Steam's app info (Valve Anti-Cheat category); flip it if a " +
        "VAC game is misdetected. SteamLite only."
private const val HELP_REMEMBER =
    "Saves this launch method for this game and skips the popup next time. You can change it later."
private const val HELP_DETAILS =
    "Opens the full Steam game page — achievements grid, DLC and cloud saves."
private const val HELP_STEAMLITE_CLIENT =
    "WinHub's small Steam client for online (VAC) launches. Newer versions add features the app " +
        "relies on (live launch status, in-game friends). Update downloads ~18 MB and re-stages it into " +
        "your container."
private const val HELP_GOLDBERG_MODE =
    "Regular suits most games. Experimental turns on newer features for games Regular can't run. " +
        "ColdClient runs the game's own launcher (heaviest). Try Regular first."

/**
 * The launch-method chooser popup — a COMPACT centered dialog that pops before a game launches. It is
 * both source-adaptive AND orientation-adaptive:
 *
 *  • **Source** — Steam gets all three methods (SteamLite / Goldberg / Raw), the Goldberg-mode selector,
 *    the SteamLite Controller-passthrough toggle, and a "Full details & achievements" link into
 *    [SteamGameDetailActivity]. Epic / GOG / Custom gray those out and offer only Raw.
 *  • **Orientation** — PORTRAIT is a compact vertical box (steamlite-launch-mockup.html); LANDSCAPE is a
 *    wide "cover-art hero" card with the art on the left and all controls on the right
 *    (steamlite-launch-landscape-mockup.html); the Goldberg selector switches from a vertical dropdown to
 *    a horizontal segmented outlined menu to fit the shorter height.
 *
 * This composable only REPORTS the choice back via [onLaunch]; the caller persists the shortcut extras
 * (`launchMode` / `launchModeRemembered` / `controllerPassthrough` / `steamVacLaunch`), stages the picked
 * component, and launches. State is keyed on [shortcut] so reopening for a different game re-seeds from
 * its saved choice. `steamVacLaunch` ("" = follow the app-info VAC detection, "1"/"0" = user override)
 * feeds the RealSteam launch's WN_STEAM_VAC secure-launch policy (see [RealSteamLauncher.prepare]).
 *
 * [onVerifyFiles] / [onUpdateFiles] drive the slim Steam-only maintenance row (a "Verify files" +
 * "Check for updates" pair, [MaintenanceRow]) shown above the pinned Launch footer — a compact stand-in
 * for real Steam's per-game "Verify integrity" / "Update". Non-null only for Steam shortcuts (the caller
 * wires them to [SteamGameUpdater.verifyFiles] / [SteamGameUpdater.updateNow]); the row is hidden for
 * Epic / GOG / Custom sources.
 */
@Composable
fun LaunchMethodSheet(
    shortcut: Shortcut,
    onDismiss: () -> Unit,
    onLaunch: (mode: String, goldbergMode: GoldbergMode?, remember: Boolean, controllerPassthrough: Boolean, vacLaunch: String) -> Unit,
    onVerifyFiles: (() -> Unit)? = null,
    onUpdateFiles: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary

    val source = remember(shortcut) { classifySource(shortcut) }
    val appId = remember(shortcut) { shortcut.getExtra("steamAppId", "").toIntOrNull() ?: 0 }
    val isSteam = source == GameSource.STEAM
    val hasDetails = isSteam && appId > 0

    // Xbox: Raw and Stream only; Stream is enabled once the cached library says the game streams.
    var streams by remember(shortcut) { mutableStateOf(false) }
    // A not-installed Xbox entry (added to Games before download): Raw .exe becomes Download, or "Not on PC"
    // for a console-only game.
    val notInstalled = remember(shortcut) { com.winhub.hernandez.store.xbox.XboxCloudShortcuts.isNotInstalled(shortcut) }
    var hasPc by remember(shortcut) { mutableStateOf(true) }
    LaunchedEffect(shortcut) {
        if (source == GameSource.XBOX) withContext(Dispatchers.IO) {
            com.winhub.hernandez.store.xbox.XboxCloudShortcuts.cachedGame(context, shortcut.getExtra("xboxProductId", ""))
        }.let { game -> streams = game?.cloud == true; hasPc = game?.pc ?: true }
    }
    val enabledMethods = remember(shortcut, streams, hasPc) {
        when {
            isSteam -> listOf(LaunchMethod.STEAMLITE, LaunchMethod.GOLDBERG, LaunchMethod.RAW)
            source == GameSource.XBOX -> listOfNotNull(LaunchMethod.RAW.takeIf { !notInstalled || hasPc }, LaunchMethod.STREAM.takeIf { streams })
            else -> listOf(LaunchMethod.RAW)
        }
    }

    SteamPrefs.init(context)
    var method by remember(shortcut) {
        mutableStateOf(
            when (shortcut.getExtra("launchMode", "")) {
                "Goldberg" -> LaunchMethod.GOLDBERG
                "Raw" -> LaunchMethod.RAW
                "RealSteam" -> LaunchMethod.STEAMLITE
                "Stream" -> if (source == GameSource.XBOX) LaunchMethod.STREAM else LaunchMethod.RAW
                else -> if (isSteam) LaunchMethod.STEAMLITE else if (notInstalled) LaunchMethod.STREAM else LaunchMethod.RAW
            }.let { if (it in enabledMethods || it == LaunchMethod.STREAM) it else LaunchMethod.RAW },
        )
    }
    var goldbergMode by remember(shortcut) {
        mutableStateOf(SteamPrefs.getGoldbergMode(appId).let { if (it == GoldbergMode.OFF) GoldbergMode.REGULAR else it })
    }
    var rememberChoice by remember(shortcut) { mutableStateOf(shortcut.getExtra("launchModeRemembered", "") == "1") }
    var controllerPassthrough by remember(shortcut) { mutableStateOf(shortcut.getExtra("controllerPassthrough", "") == "1") }
    // "Requires secure (VAC) launch" (SteamLite only). Seeded from the saved override, else from the
    // VAC marker the library sync recorded from PICS app-info (loaded off-main). Persisted only once the
    // user touches it, so an untouched toggle keeps following the detection.
    val vacOverride = remember(shortcut) { shortcut.getExtra("steamVacLaunch", "").trim() }
    var detectedVac by remember(shortcut) { mutableStateOf<Boolean?>(null) }
    var secureLaunch by remember(shortcut) { mutableStateOf(vacOverride == "1") }
    var vacTouched by remember(shortcut) { mutableStateOf(false) }
    LaunchedEffect(shortcut) {
        if (isSteam && appId > 0) {
            val detected = withContext(Dispatchers.IO) {
                runCatching { SteamRepository.getInstance().getDatabase().isVacSecure(appId) }.getOrDefault(false)
            }
            detectedVac = detected
            if (vacOverride != "1" && vacOverride != "0" && !vacTouched) secureLaunch = detected
        }
    }
    // The active "?" help bubble (null = none). Keyed on the shortcut so it resets per game.
    var helpText by remember(shortcut) { mutableStateOf<String?>(null) }
    val toggleHelp: (String) -> Unit = { helpText = if (helpText == it) null else it }

    val doLaunch: () -> Unit = {
        onLaunch(
            method.mode,
            if (method == LaunchMethod.GOLDBERG) goldbergMode else null,
            rememberChoice,
            if (method == LaunchMethod.STEAMLITE) controllerPassthrough else false,
            if (vacTouched) (if (secureLaunch) "1" else "0") else vacOverride,
        )
    }
    val openDetails: () -> Unit = {
        context.startActivity(
            Intent(context, SteamGameDetailActivity::class.java)
                .putExtra(SteamGameDetailActivity.EXTRA_APP_ID, appId),
        )
        onDismiss()
    }

    // SteamLite package check (the SteamLite chip's badge + the footer's "Update & Launch" gate).
    // Bounded ~5 s on a worker; a failed check just reads "couldn't check" and Launch stays plain.
    // Only for Steam games with the package on disk — a missing package is fetched by the launch.
    val steamLiteInstalled = remember(shortcut) { isSteam && SteamLiteComponent.isInstalled(context) }
    var clientCheck by remember(shortcut) { mutableStateOf<SteamLiteComponent.UpdateCheck?>(null) }
    var clientUpdating by remember(shortcut) { mutableStateOf(false) }
    var clientProgress by remember(shortcut) { mutableStateOf(0f) }
    var clientNote by remember(shortcut) { mutableStateOf<String?>(null) }
    // Cleared when the sheet goes away so a download that finishes after Cancel never launches.
    val alive = remember(shortcut) { AtomicBoolean(true) }
    DisposableEffect(shortcut) { onDispose { alive.set(false) } }
    LaunchedEffect(shortcut) {
        if (steamLiteInstalled) SteamLiteComponent.checkUpdateAsync(context) { clientCheck = it }
    }
    // "Update & Launch": download with progress on the chip, then the normal launch (the pre-flight
    // that follows re-checks and reads "Up to date"). A failed download is one line + a toast and
    // the launch goes ahead on the installed package — never a block.
    val updateAndLaunch: () -> Unit = {
        val check = clientCheck
        if (check != null && !clientUpdating) {
            clientUpdating = true
            clientProgress = 0f
            clientNote = null
            SteamLiteComponent.downloadAsync(
                context,
                { f -> clientProgress = f },
                { ok, _ ->
                    if (alive.get()) {
                        clientUpdating = false
                        if (ok) {
                            clientCheck = check.copy(installed = check.latestVersion)
                        } else {
                            val installed = SteamLiteComponent.versionLabel(check.installed)
                            clientNote = "Update failed — launching installed $installed"
                            Toast.makeText(context, "SteamLite update failed — launching installed $installed", Toast.LENGTH_LONG).show()
                        }
                        doLaunch()
                    }
                },
            )
        }
    }
    val steamLiteClient = if (steamLiteInstalled) {
        SteamLiteClientUi(clientCheck, clientUpdating, clientProgress, clientNote, updateAndLaunch)
    } else null

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val rawLabel = if (notInstalled) (if (hasPc) "Download" else "Not on PC") else "Raw .exe"

    CompositionLocalProvider(LocalRawLabel provides rawLabel) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        if (landscape) {
            LandscapeCard(
                shortcut, source, appId, isSteam, hasDetails, enabledMethods, accent,
                method, { method = it }, goldbergMode, { goldbergMode = it },
                rememberChoice, { rememberChoice = it }, controllerPassthrough, { controllerPassthrough = it },
                secureLaunch, { secureLaunch = it; vacTouched = true }, detectedVac,
                helpText, toggleHelp, { helpText = null }, onDismiss, doLaunch, openDetails,
                onVerifyFiles, onUpdateFiles, steamLiteClient,
            )
        } else {
            PortraitCard(
                shortcut, source, appId, isSteam, hasDetails, enabledMethods, accent,
                method, { method = it }, goldbergMode, { goldbergMode = it },
                rememberChoice, { rememberChoice = it }, controllerPassthrough, { controllerPassthrough = it },
                secureLaunch, { secureLaunch = it; vacTouched = true }, detectedVac,
                helpText, toggleHelp, { helpText = null }, onDismiss, doLaunch, openDetails,
                onVerifyFiles, onUpdateFiles, steamLiteClient,
            )
        }
    }
    }
}

/** Xbox: what the Raw chip is for this entry ("Raw .exe"; "Download" or "Not on PC" before it is installed). */
private val LocalRawLabel = compositionLocalOf { "Raw .exe" }

// ── Portrait: compact vertical box ────────────────────────────────────────────────────────────────

@Composable
private fun PortraitCard(
    shortcut: Shortcut,
    source: GameSource,
    appId: Int,
    isSteam: Boolean,
    hasDetails: Boolean,
    enabledMethods: List<LaunchMethod>,
    accent: Color,
    method: LaunchMethod,
    onMethod: (LaunchMethod) -> Unit,
    goldbergMode: GoldbergMode,
    onGoldbergMode: (GoldbergMode) -> Unit,
    rememberChoice: Boolean,
    onRemember: (Boolean) -> Unit,
    passthrough: Boolean,
    onPassthrough: (Boolean) -> Unit,
    secureLaunch: Boolean,
    onSecureLaunch: (Boolean) -> Unit,
    detectedVac: Boolean?,
    helpText: String?,
    toggleHelp: (String) -> Unit,
    dismissHelp: () -> Unit,
    onDismiss: () -> Unit,
    doLaunch: () -> Unit,
    openDetails: () -> Unit,
    onVerifyFiles: (() -> Unit)?,
    onUpdateFiles: (() -> Unit)?,
    steamLiteClient: SteamLiteClientUi?,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.padding(horizontal = 20.dp).width(340.dp),
        shape = RoundedCornerShape(18.dp),
        color = cs.surface,
        contentColor = cs.onSurface,
        border = BorderStroke(1.dp, cs.outline),
        shadowElevation = 24.dp,
    ) {
        Box {
            Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState())) {
                // Header: cover + name + source subline + close ✕.
                Row(
                    Modifier.fillMaxWidth().padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GameCover(shortcut, accent, 42.dp, 56.dp)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            shortcut.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = cs.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            sourceSubline(source, appId),
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    CloseButton(onDismiss)
                }

                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                    MicroLabel("Launch with")
                    Spacer(Modifier.height(7.dp))
                    ChipsRow(method, enabledMethods, accent, compact = false, onMethod, toggleHelp, xbox = source == GameSource.XBOX, rawLabel = LocalRawLabel.current)
                    Spacer(Modifier.height(9.dp))
                    MethodDesc(method, source)
                    if (method == LaunchMethod.STEAMLITE && steamLiteClient != null) {
                        SteamLiteClientBlock(steamLiteClient, accent, compact = false, toggleHelp, doLaunch)
                    }

                    AnimatedVisibility(visible = method == LaunchMethod.GOLDBERG) {
                        Column {
                            Spacer(Modifier.height(10.dp))
                            GoldbergDropdownPortrait(goldbergMode, accent, onGoldbergMode, toggleHelp)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = cs.outline)
                    Spacer(Modifier.height(2.dp))
                    OptionsBlock(
                        shortcut, isSteam, hasDetails, passthrough, onPassthrough,
                        secureLaunch, onSecureLaunch, detectedVac,
                        rememberChoice, onRemember, accent, toggleHelp, openDetails, compact = false,
                    )

                    // Steam-only game-files maintenance (slim row, above the pinned Launch footer).
                    if (isSteam && appId > 0 && (onVerifyFiles != null || onUpdateFiles != null)) {
                        Spacer(Modifier.height(12.dp))
                        MaintenanceRow(accent, compact = false, onVerifyFiles, onUpdateFiles)
                    }
                }

                HorizontalDivider(color = cs.outline)
                val footer = footerLaunch(method, steamLiteClient, doLaunch)
                FooterRow(accent, onDismiss, footer.second, footer.first, topPad = 10.dp, bottomPad = 12.dp, startPad = 8.dp, endPad = 14.dp)
            }
            if (helpText != null) HelpTip(helpText, dismissHelp)
        }
    }
}

// ── Landscape: wide cover-art hero (art left, controls right) ─────────────────────────────────────

@Composable
private fun LandscapeCard(
    shortcut: Shortcut,
    source: GameSource,
    appId: Int,
    isSteam: Boolean,
    hasDetails: Boolean,
    enabledMethods: List<LaunchMethod>,
    accent: Color,
    method: LaunchMethod,
    onMethod: (LaunchMethod) -> Unit,
    goldbergMode: GoldbergMode,
    onGoldbergMode: (GoldbergMode) -> Unit,
    rememberChoice: Boolean,
    onRemember: (Boolean) -> Unit,
    passthrough: Boolean,
    onPassthrough: (Boolean) -> Unit,
    secureLaunch: Boolean,
    onSecureLaunch: (Boolean) -> Unit,
    detectedVac: Boolean?,
    helpText: String?,
    toggleHelp: (String) -> Unit,
    dismissHelp: () -> Unit,
    onDismiss: () -> Unit,
    doLaunch: () -> Unit,
    openDetails: () -> Unit,
    onVerifyFiles: (() -> Unit)?,
    onUpdateFiles: (() -> Unit)?,
    steamLiteClient: SteamLiteClientUi?,
) {
    val cs = MaterialTheme.colorScheme
    val cfg = LocalConfiguration.current
    // Fit-to-screen: never exceed the device's landscape bounds (minus dialog margins), so short or
    // small screens can't clip the dialog. The controls below scroll; the Launch footer is pinned.
    val dialogW = minOf(580, cfg.screenWidthDp - 24).coerceAtLeast(320).dp
    val dialogH = minOf(332, cfg.screenHeightDp - 24).coerceAtLeast(220).dp
    Surface(
        modifier = Modifier.padding(16.dp).width(dialogW).height(dialogH),
        shape = RoundedCornerShape(18.dp),
        color = cs.surface,
        contentColor = cs.onSurface,
        border = BorderStroke(1.dp, cs.outline),
        shadowElevation = 26.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                // Left: cover-art hero with name + source overlaid at the bottom of the art.
                ArtHero(shortcut, source, appId, accent, Modifier.fillMaxHeight().width(200.dp))

                // Right: all controls.
                Column(Modifier.weight(1f).fillMaxHeight().padding(start = 13.dp, top = 11.dp, end = 13.dp, bottom = 11.dp)) {
                    // Scrollable controls: whatever can't fit the fixed-height card scrolls, so the
                    // pinned Launch footer below is NEVER clipped (landscape cut-off fix).
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            CloseButton(onDismiss, size = 24.dp)
                        }
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MicroLabel("Launch with")
                            Spacer(Modifier.width(6.dp))
                            HelpDot(accent, highlighted = false, onClick = { toggleHelp(HELP_LAUNCH_WITH) })
                        }
                        Spacer(Modifier.height(6.dp))
                        ChipsRow(method, enabledMethods, accent, compact = true, onMethod, toggleHelp, xbox = source == GameSource.XBOX, rawLabel = LocalRawLabel.current)
                        Spacer(Modifier.height(6.dp))
                        MethodDesc(method, source)
                        if (method == LaunchMethod.STEAMLITE && steamLiteClient != null) {
                            SteamLiteClientBlock(steamLiteClient, accent, compact = true, toggleHelp, doLaunch)
                        }

                        AnimatedVisibility(visible = method == LaunchMethod.GOLDBERG) {
                            Column {
                                Spacer(Modifier.height(8.dp))
                                GoldbergSegmentedLandscape(goldbergMode, accent, onGoldbergMode, toggleHelp)
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = cs.outline)
                        OptionsBlock(
                            shortcut, isSteam, hasDetails, passthrough, onPassthrough,
                            secureLaunch, onSecureLaunch, detectedVac,
                            rememberChoice, onRemember, accent, toggleHelp, openDetails, compact = true,
                        )

                        // Steam-only game-files maintenance (slim row). It lives INSIDE this scroll
                        // region, so on short screens it scrolls rather than pushing the pinned footer off.
                        if (isSteam && appId > 0 && (onVerifyFiles != null || onUpdateFiles != null)) {
                            Spacer(Modifier.height(10.dp))
                            MaintenanceRow(accent, compact = true, onVerifyFiles, onUpdateFiles)
                        }
                    }
                    // Launch/Cancel footer — pinned OUTSIDE the scroll region so it is always visible.
                    HorizontalDivider(color = cs.outline)
                    val footer = footerLaunch(method, steamLiteClient, doLaunch)
                    FooterRow(accent, onDismiss, footer.second, footer.first, topPad = 8.dp, bottomPad = 0.dp, startPad = 0.dp, endPad = 0.dp)
                }
            }
            if (helpText != null) HelpTip(helpText, dismissHelp)
        }
    }
}

// ── Shared content pieces ─────────────────────────────────────────────────────────────────────────

/** The 3-chip "Launch with" segmented selector. */
@Composable
private fun ChipsRow(
    method: LaunchMethod,
    enabledMethods: List<LaunchMethod>,
    accent: Color,
    compact: Boolean,
    onMethod: (LaunchMethod) -> Unit,
    toggleHelp: (String) -> Unit,
    xbox: Boolean = false,
    /** Xbox: the Raw chip's label ("Raw .exe", "Download" or "Not on PC"). */
    rawLabel: String = "Raw .exe",
) {
    // Xbox games have no Steam layer: only Raw .exe and Stream.
    if (xbox) {
        Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 6.dp)) {
            SegChip(if (rawLabel == "Raw .exe") "▶️" else "⬇️", rawLabel, method == LaunchMethod.RAW, LaunchMethod.RAW in enabledMethods,
                accent, compact, { onMethod(LaunchMethod.RAW) }, { toggleHelp(HELP_RAW) })
            SegChip("☁️", "Stream", method == LaunchMethod.STREAM, LaunchMethod.STREAM in enabledMethods,
                accent, compact, { onMethod(LaunchMethod.STREAM) }, { toggleHelp(HELP_STREAM) })
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 6.dp)) {
        SegChip("🌐", "SteamLite", method == LaunchMethod.STEAMLITE, LaunchMethod.STEAMLITE in enabledMethods,
            accent, compact, { onMethod(LaunchMethod.STEAMLITE) }, { toggleHelp(HELP_STEAMLITE) })
        SegChip("🛡️", "Goldberg", method == LaunchMethod.GOLDBERG, LaunchMethod.GOLDBERG in enabledMethods,
            accent, compact, { onMethod(LaunchMethod.GOLDBERG) }, { toggleHelp(HELP_GOLDBERG) })
        SegChip("▶️", "Raw .exe", method == LaunchMethod.RAW, LaunchMethod.RAW in enabledMethods,
            accent, compact, { onMethod(LaunchMethod.RAW) }, { toggleHelp(HELP_RAW) })
    }
}

/** One chip: emoji + label + a corner "?" help bubble. Selected = accent tint; disabled = dimmed. */
@Composable
private fun RowScope.SegChip(
    emoji: String,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    accent: Color,
    compact: Boolean,
    onClick: () -> Unit,
    onHelp: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val alpha = if (enabled) 1f else 0.38f
    Box(Modifier.weight(1f)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(if (compact) 9.dp else 11.dp))
                .background(if (selected) accent.copy(alpha = 0.14f) else cs.surfaceContainerHigh)
                .border(1.dp, if (selected) accent else cs.outline, RoundedCornerShape(if (compact) 9.dp else 11.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = if (compact) 6.dp else 8.dp, horizontal = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(emoji, fontSize = if (compact) 13.sp else 16.sp, modifier = Modifier.alpha(alpha))
            Spacer(Modifier.height(if (compact) 2.dp else 4.dp))
            Text(
                label,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                color = when {
                    !enabled -> cs.onSurfaceVariant.copy(alpha = 0.5f)
                    selected -> accent
                    else -> cs.onSurface
                },
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Corner "?" — tappable even when the chip itself is disabled (explains why it's unavailable).
        HelpDot(
            accent = accent,
            highlighted = selected,
            onClick = onHelp,
            modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
        )
    }
}

/** The one-line description that updates per selected method. */
@Composable
private fun MethodDesc(method: LaunchMethod, source: GameSource) {
    Text(
        methodDescription(method, source),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 15.sp,
    )
}

/** The option rows: Full details (Steam), Controller passthrough (Steam), Requires secure (VAC) launch (Steam), Remember. */
@Composable
private fun ColumnScope.OptionsBlock(
    shortcut: Shortcut,
    isSteam: Boolean,
    hasDetails: Boolean,
    passthrough: Boolean,
    onPassthrough: (Boolean) -> Unit,
    secureLaunch: Boolean,
    onSecureLaunch: (Boolean) -> Unit,
    detectedVac: Boolean?,
    rememberChoice: Boolean,
    onRemember: (Boolean) -> Unit,
    accent: Color,
    toggleHelp: (String) -> Unit,
    openDetails: () -> Unit,
    compact: Boolean,
) {
    if (hasDetails) {
        OptionRow(
            title = "Full details & achievements",
            badge = null,
            subtitle = if (compact) null else "Details, achievements, DLC and cloud saves.",
            accent = accent,
            compact = compact,
            onHelp = { toggleHelp(HELP_DETAILS) },
            onRowClick = openDetails,
            trailing = {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Open details", tint = accent, modifier = Modifier.size(20.dp))
            },
        )
    }
    if (isSteam) {
        OptionRow(
            title = "Controller passthrough",
            badge = "NEW",
            subtitle = if (compact) null else "Send the pad straight to the game. For classic games (HL2, CS:S).",
            accent = accent,
            compact = compact,
            onHelp = { toggleHelp(HELP_PASS) },
            trailing = { PillSwitch(passthrough, accent, onPassthrough) },
        )
        OptionRow(
            title = "Requires secure (VAC) launch",
            badge = "NEW",
            subtitle = if (compact) null else when (detectedVac) {
                true -> "Detected: VAC-secured. Steam must start it (up to ~60 s wait)."
                false -> "Detected: no VAC. Direct start after ~15 s is fine."
                null -> "Auto-detected from Steam's app info."
            },
            accent = accent,
            compact = compact,
            onHelp = { toggleHelp(HELP_VAC) },
            trailing = { PillSwitch(secureLaunch, accent, onSecureLaunch) },
        )
    }
    OptionRow(
        title = "Remember my choice",
        badge = null,
        subtitle = if (compact) null else "Skip this popup next time for ${shortcut.name}.",
        accent = accent,
        compact = compact,
        onHelp = { toggleHelp(HELP_REMEMBER) },
        trailing = { PillSwitch(rememberChoice, accent, onRemember) },
    )
}

/** A title (+ optional badge / subtitle) with a "?" help bubble and a trailing control. The whole row is
 *  tappable when [onRowClick] is set (the "Full details" link). */
@Composable
private fun OptionRow(
    title: String,
    badge: String?,
    subtitle: String?,
    accent: Color,
    compact: Boolean,
    onHelp: () -> Unit,
    onRowClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onRowClick != null) Modifier.clickable(onClick = onRowClick) else Modifier)
            .padding(vertical = if (compact) 6.dp else 9.dp, horizontal = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    color = cs.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (badge != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, accent, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, lineHeight = 14.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        HelpDot(accent, highlighted = false, onClick = onHelp)
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}

/**
 * What the footer's primary button says and does for the selected method: plain "Launch" unless the
 * SteamLite chip is selected and its package check gates the launch ("Update & Launch"; disabled
 * "Updating…" while the download runs). Goldberg / Raw are never gated.
 */
private fun footerLaunch(
    method: LaunchMethod,
    client: SteamLiteClientUi?,
    doLaunch: () -> Unit,
): Pair<String, (() -> Unit)?> {
    if (method != LaunchMethod.STEAMLITE || client == null) return "Launch" to doLaunch
    return when {
        client.updating -> "Updating…" to null
        client.gated -> "Update & Launch" to client.onUpdateAndLaunch
        else -> "Launch" to doLaunch
    }
}

/** Cancel (text) · spacer · small accent "Launch ▶" ([launch] null = disabled, e.g. mid-download). */
@Composable
private fun FooterRow(
    accent: Color,
    onDismiss: () -> Unit,
    launch: (() -> Unit)?,
    launchLabel: String,
    topPad: androidx.compose.ui.unit.Dp,
    bottomPad: androidx.compose.ui.unit.Dp,
    startPad: androidx.compose.ui.unit.Dp,
    endPad: androidx.compose.ui.unit.Dp,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(top = topPad, bottom = bottomPad, start = startPad, end = endPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onDismiss) {
            Text("Cancel", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { launch?.invoke() },
            enabled = launch != null,
            shape = RoundedCornerShape(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 9.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = cs.onPrimary),
        ) {
            Text(launchLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(7.dp))
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * Under the SteamLite chip: the package's status line — checking / couldn't check / up to date /
 * "Update available (v3 → v4)" badge (error-tinted "Update required" below
 * [SteamLiteComponent.MIN_AGENT_VERSION]) / the download's progress — with a "?" bubble, and,
 * when the update is optional, a quieter "Launch with installed version" link (the footer's primary
 * is "Update & Launch" then). Absent for Goldberg / Raw.
 */
@Composable
private fun SteamLiteClientBlock(
    ui: SteamLiteClientUi,
    accent: Color,
    compact: Boolean,
    toggleHelp: (String) -> Unit,
    doLaunch: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val check = ui.check
    val installed = SteamLiteComponent.versionLabel(check?.installed ?: 0)
    val lineStyle = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
    Spacer(Modifier.height(if (compact) 6.dp else 8.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        when {
            ui.updating -> Text(
                "Updating SteamLite… ${(ui.progress.coerceIn(0f, 1f) * 100).toInt()}%",
                style = lineStyle, color = cs.onSurface, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            ui.note != null -> Text(ui.note, style = lineStyle, color = cs.error, modifier = Modifier.weight(1f))
            check == null -> Text("Checking for SteamLite updates…", style = lineStyle, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
            !check.checked -> Text(
                "Couldn't check for SteamLite updates — using installed $installed",
                style = lineStyle, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f),
            )
            check.available -> {
                // Badge: accent pill for an optional update, error pill when the app needs it.
                val tint = if (check.required) cs.error else accent
                val shape = RoundedCornerShape(999.dp)
                Text(
                    (if (check.required) "Update required" else "Update available") +
                        " ($installed → v${check.latestVersion})",
                    style = lineStyle, color = tint, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(shape)
                        .background(tint.copy(alpha = 0.14f))
                        .border(1.dp, tint.copy(alpha = 0.55f), shape)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Spacer(Modifier.weight(1f))
            }
            else -> Text("SteamLite client up to date ($installed)", style = lineStyle, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.width(6.dp))
        HelpDot(accent, highlighted = false, onClick = { toggleHelp(HELP_STEAMLITE_CLIENT) })
    }
    if (ui.updating) {
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { ui.progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = accent,
            trackColor = cs.surfaceContainerHigh,
        )
    } else if (ui.gated && check?.required == false) {
        // Optional update: the quieter way past the "Update & Launch" footer.
        Text(
            "Launch with installed version",
            style = lineStyle, color = cs.onSurfaceVariant, fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = doLaunch)
                .padding(horizontal = 4.dp, vertical = 3.dp),
        )
    }
}

/** Steam-only game-files maintenance: a slim single row of two compact buttons — "Verify files"
 *  (full integrity re-validate → [SteamGameUpdater.verifyFiles]) and "Check for updates" (delta update →
 *  [SteamGameUpdater.updateNow]). The two hand off to the caller's [onVerify] / [onUpdate] lambdas, which
 *  run the SAME shared maintenance path as the game-menu action. Deliberately compact (one short row, no
 *  status box) so it fits inside the scroll area above the pinned Launch footer in both orientations. */
@Composable
private fun MaintenanceRow(
    accent: Color,
    compact: Boolean,
    onVerify: (() -> Unit)?,
    onUpdate: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
    ) {
        if (onVerify != null) {
            MaintButton("Verify files", Icons.Filled.Refresh, accent, compact, Modifier.weight(1f), onVerify)
        }
        if (onUpdate != null) {
            MaintButton("Check for updates", Icons.Filled.Download, accent, compact, Modifier.weight(1f), onUpdate)
        }
    }
}

/** One compact maintenance button: accent icon + short label in a bordered pill, matching the sheet's
 *  chip idiom. Sizes to its [modifier] (the row gives each an equal weight so the pair never overflows);
 *  the label ellipsizes on very narrow widths rather than wrapping or pushing the row wider. */
@Composable
private fun MaintButton(
    label: String,
    icon: ImageVector,
    accent: Color,
    compact: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(if (compact) 9.dp else 10.dp)
    Row(
        modifier
            .clip(shape)
            .background(cs.surfaceContainerHigh)
            .border(1.dp, cs.outline, shape)
            .clickable(onClick = onClick)
            .padding(vertical = if (compact) 6.dp else 8.dp, horizontal = if (compact) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(if (compact) 14.dp else 16.dp))
        Spacer(Modifier.width(if (compact) 5.dp else 7.dp))
        Text(
            label,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            color = cs.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Header / art ──────────────────────────────────────────────────────────────────────────────────

/** The compact poster cover (portrait header): the shortcut's own cover-art file / bitmap via Coil, else
 *  an initials placeholder. Reuses the same cover pipeline the grid tiles use, so it is source-agnostic. */
@Composable
private fun GameCover(shortcut: Shortcut, accent: Color, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp) {
    val cs = MaterialTheme.colorScheme
    val model = rememberCoverModel(shortcut)
    Box(
        Modifier.size(width = w, height = h).clip(RoundedCornerShape(7.dp)).border(1.dp, cs.outline, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(
                Modifier.fillMaxSize().background(Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), cs.surfaceVariant))),
                contentAlignment = Alignment.Center,
            ) {
                Text(shortcut.name.take(2).uppercase(), style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** The landscape left pane: cover art filling the pane, a bottom fade, and the game name + source
 *  overlaid at the bottom-left of the art. */
@Composable
private fun ArtHero(shortcut: Shortcut, source: GameSource, appId: Int, accent: Color, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val model = rememberCoverModel(shortcut)
    Box(modifier.background(Brush.linearGradient(listOf(accent.copy(alpha = 0.45f), cs.surfaceVariant)))) {
        if (model != null) {
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(shortcut.name.take(1).uppercase(), fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.14f))
            }
        }
        // Bottom fade so the caption stays legible over any art.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.6f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f)))),
        )
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp)) {
            Text(
                shortcut.name,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(sourceSubline(source, appId), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

/** The shortcut's cover model for Coil: its custom cover-art file (the extra Steam/Epic/GOG imports write),
 *  else its decoded cover/icon bitmap, else null (→ initials placeholder). Source-agnostic. */
@Composable
private fun rememberCoverModel(shortcut: Shortcut): Any? = remember(shortcut) {
    val path = shortcut.customCoverArtPath
    when {
        !path.isNullOrEmpty() && File(path).exists() -> File(path)
        shortcut.coverArt != null -> shortcut.coverArt
        shortcut.icon != null -> shortcut.icon
        else -> null
    }
}

// ── Small shared widgets ──────────────────────────────────────────────────────────────────────────

@Composable
private fun CloseButton(onDismiss: () -> Unit, size: androidx.compose.ui.unit.Dp = 28.dp) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(size).clip(RoundedCornerShape(8.dp)).border(1.dp, cs.outline, RoundedCornerShape(8.dp)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Close, contentDescription = "Close", tint = cs.onSurfaceVariant, modifier = Modifier.size(size * 0.55f))
    }
}

/** The small circular "?" that opens a help bubble. */
@Composable
private fun HelpDot(accent: Color, highlighted: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier.size(16.dp).clip(CircleShape)
            .background(if (highlighted) accent.copy(alpha = 0.16f) else cs.surfaceVariant)
            .border(1.dp, if (highlighted) accent.copy(alpha = 0.55f) else cs.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("?", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = if (highlighted) accent else cs.onSurfaceVariant, fontWeight = FontWeight.Bold)
    }
}

/** M3 Switch styled as the mockup's accent pill toggle. */
@Composable
private fun PillSwitch(checked: Boolean, accent: Color, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accent, checkedBorderColor = accent),
    )
}

@Composable
private fun MicroLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.0.sp,
    )
}

/** The floating dark help tooltip (the mockup's `.tip`), pinned above the footer. Tap to dismiss. */
@Composable
private fun BoxScope.HelpTip(text: String?, onDismiss: () -> Unit) {
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .padding(bottom = 48.dp)
            .widthIn(max = 300.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF111114))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text.orEmpty(), style = MaterialTheme.typography.labelMedium, color = Color(0xFFECECF0), lineHeight = 15.sp)
    }
}

// ── Source-adaptive copy ──────────────────────────────────────────────────────────────────────────

private fun sourceSubline(source: GameSource, appId: Int): String = when (source) {
    GameSource.STEAM -> if (appId > 0) "Steam · App $appId" else "Steam"
    GameSource.EPIC -> "Epic · Raw launch"
    GameSource.GOG -> "GOG · DRM-free · Raw"
    GameSource.AMAZON -> "Amazon Games · Raw"
    GameSource.XBOX -> "Xbox · Licensed launch"
    GameSource.CUSTOM -> "Custom · Raw"
}

private fun methodDescription(method: LaunchMethod, source: GameSource): String = when (method) {
    LaunchMethod.STEAMLITE -> "Real Steam — VAC servers, real achievements & cloud saves."
    LaunchMethod.GOLDBERG -> "Offline emulator — no login, lightweight, single-player."
    LaunchMethod.RAW -> when (source) {
        GameSource.EPIC -> "Run the game's .exe directly."
        GameSource.GOG -> "Run the DRM-free .exe directly — no launcher."
        GameSource.AMAZON -> "Run the game's .exe directly — no Amazon Games app."
        GameSource.XBOX -> "Each launch checks the license with Microsoft and starts the game's .exe; no Xbox app."
        GameSource.CUSTOM -> "Run the .exe directly."
        GameSource.STEAM -> "Run the game's .exe directly — no Steam layer."
    }
    LaunchMethod.STREAM -> "Plays from Xbox Cloud Gaming — nothing runs here; picture, sound and controller go over the network."
}

// ── Goldberg sub-mode selectors (shared outlined-menu style: MenuStyle.kt) ────────────────────────

private data class GbOption(val mode: GoldbergMode, val name: String, val sub: String)

private val GOLDBERG_OPTIONS = listOf(
    GbOption(GoldbergMode.REGULAR, "Regular", "Standard emulation — best compatibility"),
    GbOption(GoldbergMode.EXPERIMENTAL, "Experimental", "Newer features — for games Regular can't run"),
    GbOption(GoldbergMode.COLDCLIENT, "ColdClient", "Runs the game's own launcher — heaviest option"),
)

/** Portrait: label + "?" and a vertical dropdown using the shared outlined-menu card + gray dividers. */
@Composable
private fun GoldbergDropdownPortrait(mode: GoldbergMode, accent: Color, onSelected: (GoldbergMode) -> Unit, toggleHelp: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val current = GOLDBERG_OPTIONS.firstOrNull { it.mode == mode } ?: GOLDBERG_OPTIONS.first()

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MicroLabel("Goldberg mode")
            Spacer(Modifier.width(7.dp))
            HelpDot(accent, highlighted = false, onClick = { toggleHelp(HELP_GOLDBERG_MODE) })
        }
        Spacer(Modifier.height(6.dp))
        Box {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(cs.surfaceContainerHigh)
                    .border(1.dp, cs.outline, RoundedCornerShape(10.dp)).clickable { open = true }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(current.name, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface, fontWeight = FontWeight.SemiBold)
                    Text(current.sub, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = cs.onSurfaceVariant)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.outlinedMenuCard()) {
                GOLDBERG_OPTIONS.forEachIndexed { i, opt ->
                    if (i > 0) MenuItemDivider()
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(opt.name, style = MaterialTheme.typography.bodyMedium, color = if (opt.mode == mode) accent else cs.onSurface, fontWeight = FontWeight.SemiBold)
                                Text(opt.sub, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            }
                        },
                        trailingIcon = { if (opt.mode == mode) Icon(Icons.Filled.Check, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp)) },
                        onClick = { onSelected(opt.mode); open = false },
                    )
                }
            }
        }
    }
}

/** Landscape: label + "?" and a horizontal segmented outlined menu (Regular | Experimental | ColdClient)
 *  with thin gray dividers between items — the shorter-height counterpart to the dropdown. */
@Composable
private fun GoldbergSegmentedLandscape(mode: GoldbergMode, accent: Color, onSelected: (GoldbergMode) -> Unit, toggleHelp: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MicroLabel("Goldberg mode")
            Spacer(Modifier.width(6.dp))
            HelpDot(accent, highlighted = false, onClick = { toggleHelp(HELP_GOLDBERG_MODE) })
        }
        Spacer(Modifier.height(5.dp))
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(9.dp))
                .background(cs.surfaceContainerHigh).border(1.dp, cs.outline, RoundedCornerShape(9.dp)),
        ) {
            GOLDBERG_OPTIONS.forEachIndexed { i, opt ->
                if (i > 0) VerticalDivider(color = cs.outline.copy(alpha = 0.5f))
                val sel = opt.mode == mode
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .background(if (sel) accent.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable { onSelected(opt.mode) }
                        .padding(vertical = 7.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        opt.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (sel) accent else cs.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
