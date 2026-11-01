package com.winhub.hernandez.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Warning
import com.winhub.hernandez.ui.components.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.winhub.hernandez.ui.components.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.winhub.hernandez.ui.components.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import com.winhub.hernandez.core.StringUtils
import com.winhub.hernandez.core.unpack.Innoextract
import com.winhub.hernandez.core.unpack.PowerMode
import com.winhub.hernandez.core.unpack.ReadBuffer
import com.winhub.hernandez.core.unpack.SevenZip
import com.winhub.hernandez.core.unpack.Unarc
import com.winhub.hernandez.core.unpack.UnpackManager
import com.winhub.hernandez.core.unpack.UnpackPhase
import com.winhub.hernandez.core.unpack.UnpackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The "Unpack Archive" screen: point the bundled 7-Zip engine at a disc image / archive and extract
 * it to a chosen folder, with a foreground service doing the work so it survives backgrounding.
 *
 * Reached from the File Manager's ⋮ menu (hosted by [com.winhub.hernandez.UnpackArchiveActivity]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnpackArchiveScreen(
    archivePath: String,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val selected = remember(archivePath) { File(archivePath) }
    val cores = remember { Runtime.getRuntime().availableProcessors().coerceAtLeast(1) }

    val state by UnpackManager.state.collectAsState()

    // InnoSetup repack? Then 7-Zip must be pointed at the installer .exe (never a lone Setup-*.bin),
    // and the whole flow is "unpack game payload" rather than "extract archive".
    val innoTarget = remember(archivePath) { SevenZip.resolveInnoTarget(selected) }
    val isInno = innoTarget != null
    // What 7-Zip is actually run against: the installer .exe for InnoSetup, else the file itself.
    val archive = remember(archivePath) { innoTarget ?: selected }

    // A friendly default extract-folder name: the repack folder name for InnoSetup (so "Setup" never
    // becomes the folder), else the archive's base name.
    val defaultName = remember(archivePath) {
        if (isInno) archive.parentFile?.name?.takeIf { it.isNotBlank() } ?: "game"
        else SevenZip.suggestedTargetName(selected)
    }

    // Detected type comes from a quick `7zz l` (metadata only). Keyed on the archive so it reruns if
    // the screen is reused for a different one.
    var detectedType by remember(archivePath) { mutableStateOf<String?>(null) }
    var typeLoading by remember(archivePath) { mutableStateOf(true) }
    // InnoSetup classification: most modern repacks (FitGirl/DODI) are FreeArc-compressed, which
    // 7-Zip can't open — those must be installed by running Setup.exe in a container. Classify BEFORE
    // offering a doomed 7-Zip "unpack" action (Records.ini + a `7zz l` pre-flight, off the main thread).
    var innoClass by remember(archivePath) { mutableStateOf<SevenZip.InnoClassification?>(null) }
    // GOG DLC auto-batch: sibling setup_*.exe installers (base game first, then any DLC/extra setups)
    // in the same folder. Populated for the innoextract route only; size > 1 means DLC was found, which
    // reveals the "also extract DLC" toggle below. Default the toggle on — auto-batch is the ask.
    var dlcInstallers by remember(archivePath) { mutableStateOf<List<File>>(emptyList()) }
    var alsoExtractDlc by remember(archivePath) { mutableStateOf(true) }
    LaunchedEffect(archivePath) {
        typeLoading = true
        if (isInno) {
            innoClass = withContext(Dispatchers.IO) { SevenZip.classifyInno(context, archive) }
            if (innoClass?.route == SevenZip.InnoRoute.INNOEXTRACT) {
                dlcInstallers = withContext(Dispatchers.IO) { SevenZip.siblingInnoInstallers(archive) }
            }
        } else {
            val info = withContext(Dispatchers.IO) { SevenZip.list(context, archive) }
            detectedType = info?.type
        }
        typeLoading = false
    }

    // Destination defaults to a sibling folder (of the repack folder, for InnoSetup) named for the game.
    var destPath by remember(archivePath) {
        val base = if (isInno) archive.parentFile?.parentFile ?: archive.parentFile else selected.parentFile
        mutableStateOf(File(base, defaultName).absolutePath)
    }
    val destPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            com.winhub.hernandez.util.InAppFilePicker.pickedPath(result.data)?.let {
                // Land inside the chosen folder, in a subfolder named for the game, so the extract
                // never carpets someone's Games root with loose files.
                destPath = File(it, defaultName).absolutePath
            }
        }
    }

    var powerMode by remember { mutableStateOf(PowerMode.MAX) }
    var manualCores by remember { mutableStateOf(cores) }
    var buffer by remember { mutableStateOf(ReadBuffer.MB1) }
    var bufferMenu by remember { mutableStateOf(false) }

    // Direct java.io.File writes need All Files Access; a native process can't write through SAF, so
    // when the destination is on shared storage and access isn't granted we gate extraction and send
    // the user to grant it rather than ship a half-working SAF-for-native path.
    val hasAllFiles = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
    val destOnSharedStorage = destPath.startsWith("/storage/") && !destPath.startsWith(context.filesDir.absolutePath)
    val gatedByPermission = destOnSharedStorage && !hasAllFiles

    // InnoSetup routing (see classifyInno). While classifying we hold the action buttons.
    val innoClassifying = isInno && innoClass == null
    // FreeArc/ISDone repack → decode natively with unarc; standard-Inno/GOG → innoextract; neither
    // available (or srep) → run Setup.exe in a container.
    val innoContainerOnly = innoClass?.route == SevenZip.InnoRoute.CONTAINER_ONLY
    val innoExtract = innoClass?.route == SevenZip.InnoRoute.INNOEXTRACT
    val innoFreeArc = innoClass?.route == SevenZip.InnoRoute.FREEARC_NATIVE
    // The path actually handed to the service (its job key): FreeArc runs on the first Setup-*.bin
    // volume; everything else on `archive` (the resolved Setup.exe or the plain file).
    val jobArchive = if (innoFreeArc) (innoClass?.freeArcArchive ?: archive) else archive

    val running = state.isRunning && state.archivePath == jobArchive.absolutePath
    val engineMissing = !SevenZip.isAvailable(context)

    // For display + honest speed/ETA: the payload data. For InnoSetup that's the Setup-*.bin total,
    // not the tiny Setup.exe.
    val sourceSize = remember(archivePath) {
        if (isInno) {
            archive.parentFile?.listFiles()
                ?.filter { it.isFile && (it.extension.equals("bin", true) || it == archive) }
                ?.sumOf { it.length() } ?: archive.length()
        } else selected.length()
    }

    // Only one extraction at a time (matches the service's own guard).
    val otherJobRunning = state.isRunning && state.archivePath != jobArchive.absolutePath

    // Content pre-flight (not extension): a plain file is judged by whether `7zz l` could open it as
    // an archive / disc image. No recognisable container (e.g. raw .bin data) → nothing to unpack.
    val notAnArchive = !isInno && !typeLoading && detectedType == null
    // Are we still deciding what this file is? (InnoSetup pre-flight, or the plain content-sniff.)
    val checking = if (isInno) innoClassifying else typeLoading
    // The 7-Zip extract path is for plain (non-Inno) archives only, once the sniff confirms it opens.
    val sevenZipAllowed = !isInno && !typeLoading && detectedType != null
    val engineMissingInno = innoExtract && !Innoextract.isAvailable(context)

    // Battery-optimisation exemption, refreshed on resume so returning from Settings re-checks it.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumeTick++ }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    val ignoringBattery = remember(resumeTick) {
        val pm = context.getSystemService(android.os.PowerManager::class.java)
        pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }

    // One-time dismissible aggressive-OEM hint.
    val prefs = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    var oemHintDismissed by remember { mutableStateOf(prefs.getBoolean("unpackOemHintDismissed", false)) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header bar.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                "Unpack Archive",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // ── Source ──
            SectionCard {
                Text("Source", style = sectionTitle())
                Spacer(Modifier.height(6.dp))
                Text(selected.name, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                val typeText = when {
                    isInno && innoClassifying -> "InnoSetup installer • checking…"
                    isInno -> buildString {
                        append("InnoSetup installer")
                        innoClass?.compression?.let { append(" • ").append(it) }
                        innoClass?.declaredSize?.let { append(", ").append(it) }
                    }
                    typeLoading -> "reading…"
                    detectedType != null -> detectedType
                    else -> "not an archive"
                }
                Text(
                    "${StringUtils.formatBytes(sourceSize)}  •  $typeText",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                if (innoExtract) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "innoextract will unpack the game files from ${archive.name}.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
                if (innoFreeArc) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "unarc will decode the FreeArc data (${jobArchive.name} + its Setup-*.bin volumes) directly.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Destination applies to the 7-Zip, innoextract AND FreeArc/unarc paths; Power (below) is
            // 7-Zip-only. All hidden while still deciding, for the container-only route, and non-archives.
            if (sevenZipAllowed || innoExtract || innoFreeArc) {
            // ── Destination ──
            SectionCard {
                Text("Extract to", style = sectionTitle())
                Spacer(Modifier.height(6.dp))
                Text(
                    destPath,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    enabled = !running,
                    onClick = {
                        destPicker.launch(
                            com.winhub.hernandez.util.InAppFilePicker.buildDirIntent(
                                context,
                                title = "Choose where to extract",
                                initialDir = archive.parent,
                            )
                        )
                    },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Icon(Icons.Filled.Folder, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text("Change folder", color = MaterialTheme.colorScheme.onBackground)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Power (7-Zip only; innoextract has no thread/buffer knobs) ──
            if (sevenZipAllowed) {
            SectionCard {
                Text("Power", style = sectionTitle())
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val modes = listOf(PowerMode.AUTO to "Auto", PowerMode.MAX to "Max", PowerMode.MANUAL to "Manual")
                    modes.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = powerMode == mode,
                            onClick = { if (!running) powerMode = mode },
                            shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                        ) { Text(label) }
                    }
                }
                if (powerMode == PowerMode.MANUAL) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "$manualCores of $cores cores",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                    )
                    Slider(
                        value = manualCores.toFloat(),
                        onValueChange = { if (!running) manualCores = it.toInt().coerceIn(1, cores) },
                        valueRange = 1f..cores.toFloat(),
                        steps = (cores - 2).coerceAtLeast(0),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Extraction is limited by storage speed; extra cores only help archives with " +
                        "many files (7z/solid). A single huge file won't parallelize.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )

                Spacer(Modifier.height(12.dp))

                // Read-buffer knob.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Read buffer", color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Box {
                        OutlinedButton(
                            enabled = !running,
                            onClick = { bufferMenu = true },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        ) { Text(buffer.label, color = MaterialTheme.colorScheme.onBackground) }
                        DropdownMenu(expanded = bufferMenu, onDismissRequest = { bufferMenu = false }) {
                            ReadBuffer.entries.forEach { b ->
                                DropdownMenuItem(text = { Text(b.label) }, onClick = { buffer = b; bufferMenu = false })
                            }
                        }
                    }
                }
                Text(
                    "Larger buffers can improve throughput reading from FUSE-backed storage.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
            } // end Power (7-Zip only)
            } // end Destination + Power block

            // ── Permission gate ──
            if (gatedByPermission && sevenZipAllowed) {
                WarnCard {
                    Text(
                        "All Files Access is off. Extraction writes directly to storage and can't use the " +
                            "slow SAF fallback for an 80 GB image, so grant access to continue.",
                        color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                    .setData(Uri.parse("package:${context.packageName}"))
                            )
                        }
                    }) { Text("Grant access") }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (engineMissing && !isInno) {
                WarnCard {
                    Text(
                        "The 7-Zip engine (lib7zz.so) isn't executable on this build. Extraction is unavailable.",
                        color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── Battery-optimisation exemption (recommended, non-blocking) ──
            if (!running && !ignoringBattery) {
                WarnCard {
                    Text(
                        "For a job this long, exempt the app from battery optimisation so the system " +
                            "doesn't pause or kill it in the background. Recommended for 80 GB extracts.",
                        color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                    .setData(Uri.parse("package:${context.packageName}"))
                            )
                        }
                    }) { Text("Allow background running") }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── Aggressive-OEM hint (one-time, dismissible) ──
            if (!oemHintDismissed) {
                SectionCard {
                    Text(
                        "On HONOR/Huawei/Xiaomi/OPPO devices, also lock this app in Recents and set its " +
                            "battery to \"No restrictions\", or the system may still pause background extraction.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                            .setData(Uri.parse("package:${context.packageName}"))
                                    )
                                }
                            },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        ) { Text("App settings", color = MaterialTheme.colorScheme.onBackground) }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                oemHintDismissed = true
                                prefs.edit().putBoolean("unpackOemHintDismissed", true).apply()
                            },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        ) { Text("Got it", color = MaterialTheme.colorScheme.onBackground) }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── One extraction at a time ──
            if (!running && otherJobRunning) {
                WarnCard {
                    Text(
                        "Another unpack is already in progress. Only one runs at a time — wait for it to " +
                            "finish (tap its progress pill to watch), then start this one.",
                        color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── Still deciding what this file is (InnoSetup pre-flight, or plain content-sniff) ──
            if (!running && checking) {
                SectionCard {
                    Text(
                        if (isInno) "Checking how this repack is compressed…" else "Checking this file…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── Not a recognized archive (judged by content, not extension) ──
            if (!running && notAnArchive) {
                WarnCard {
                    Text("Nothing to unpack", color = MaterialTheme.colorScheme.error, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "This file isn't a recognized archive or disc image — it looks like raw data, " +
                            "nothing to unpack.",
                        color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── FreeArc repack → native unarc (primary) ──
            if (!running && innoFreeArc) {
                SectionCard {
                    Text(
                        "FreeArc decompression is I/O-bound: unarc already auto-parallelizes (4x4), so the " +
                            "real limit is writing the game to your storage — a faster card / internal " +
                            "storage helps far more than anything else. There's no thread knob to tune.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        UnpackManager.clearIfTerminal()
                        UnpackService.start(context, jobArchive.absolutePath, destPath, 1, buffer.bytes, true, sourceSize, "unarc")
                    },
                    enabled = !gatedByPermission && jobArchive.isFile && !otherJobRunning,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Filled.Unarchive, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Unpack game natively (unarc)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                // No dead-ends: also offer the container route (e.g. srep, or if unarc fails at runtime).
                Spacer(Modifier.height(8.dp))
                RunSetupInContainer(exe = archive)
                Spacer(Modifier.height(12.dp))
            }

            // ── Can't unpack in-app (unarc unavailable / srep) → container-only route ──
            if (!running && innoContainerOnly) {
                val comp = innoClass?.compression ?: "FreeArc"
                WarnCard {
                    Text("Can't unpack this repack in-app", color = MaterialTheme.colorScheme.error, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "This is a $comp repack whose data can't be decoded in-app (it may use SREP). Install it " +
                            "by running ${archive.name} inside a Winlator container, or unpack it on a PC.",
                        color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    RunSetupInContainer(exe = archive)
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── InnoSetup / GOG → innoextract (primary) ──
            if (!running && innoExtract) {
                if (engineMissingInno) {
                    WarnCard {
                        Text(
                            "The innoextract engine isn't available on this build, so this GOG/InnoSetup " +
                                "installer can't be unpacked in-app. You can still install it by running " +
                                "${archive.name} in a container.",
                            color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        RunSetupInContainer(exe = archive)
                    }
                } else {
                    val dlcCount = (dlcInstallers.size - 1).coerceAtLeast(0)
                    val batchDlc = alsoExtractDlc && dlcCount > 0
                    // DLC/extra setups found next to the base installer: offer to extract them all into
                    // the same game folder (base first, DLC overlaid), or untick for base-game only.
                    if (dlcCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        ) {
                            Checkbox(checked = alsoExtractDlc, onCheckedChange = { alsoExtractDlc = it })
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Also extract $dlcCount DLC setup${if (dlcCount == 1) "" else "s"} found in this folder",
                                color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp,
                            )
                        }
                    }
                    Button(
                        onClick = {
                            UnpackManager.clearIfTerminal()
                            // isInno=true selects the innoextract engine in the service.
                            if (batchDlc) {
                                UnpackService.startBatch(context, dlcInstallers.map { it.absolutePath }, destPath, sourceSize)
                            } else {
                                UnpackService.start(context, archive.absolutePath, destPath, 1, buffer.bytes, true, sourceSize)
                            }
                        },
                        enabled = !gatedByPermission && archive.isFile && !otherJobRunning,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Filled.Unarchive, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (batchDlc) "Unpack game + $dlcCount DLC (innoextract)" else "Unpack game files (innoextract)",
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                    // No dead-ends: also offer the container route (e.g. if innoextract fails at runtime).
                    Spacer(Modifier.height(8.dp))
                    RunSetupInContainer(exe = archive)
                }
            }

            // ── Extract button (plain, non-Inno archives) ──
            if (!running && sevenZipAllowed) {
                Button(
                    onClick = {
                        UnpackManager.clearIfTerminal()
                        val mmt = UnpackManager.mmtFor(powerMode, manualCores)
                        UnpackService.start(context, archive.absolutePath, destPath, mmt, buffer.bytes, false, sourceSize)
                    },
                    enabled = !gatedByPermission && !engineMissing && archive.isFile && !otherJobRunning,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Filled.Unarchive, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Extract", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // ── Progress ──
            if (running) {
                Spacer(Modifier.height(4.dp))
                SectionCard {
                    val listing = state.phase == UnpackPhase.LISTING
                    Text(
                        if (listing) "Reading archive…" else "Extracting — ${state.percent}%",
                        color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (listing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(6.dp))
                    } else {
                        LinearProgressIndicator(
                            progress = { state.percent / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        buildString {
                            if (state.speedBps > 0) append("${StringUtils.formatBytes(state.speedBps)}/s")
                            if (state.etaSeconds >= 0) {
                                if (isNotEmpty()) append("  •  ")
                                append("ETA ${humanDuration(state.etaSeconds * 1000)}")
                            }
                            if (state.filesExtracted > 0) {
                                if (isNotEmpty()) append("  •  ")
                                append("${state.filesExtracted} files")
                            }
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                    )
                    state.currentFile?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Safe to leave — this keeps running in the background. Reopen it from the progress " +
                            "pill or the notification.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row {
                        OutlinedButton(
                            onClick = { UnpackService.cancel(context) },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        ) { Text("Cancel", color = MaterialTheme.colorScheme.error) }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onClose) { Text("Minimize") }
                    }
                }
            }

            // ── Terminal result ──
            if (state.archivePath == jobArchive.absolutePath && !running) {
                when (state.phase) {
                    UnpackPhase.DONE -> {
                        Spacer(Modifier.height(4.dp))
                        SectionCard {
                            Text("Done", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${state.filesExtracted} files • ${StringUtils.formatBytes(state.archiveSize)} in ${humanDuration(state.elapsedMs)}",
                                color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(state.destPath, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    UnpackPhase.ERROR -> {
                        Spacer(Modifier.height(4.dp))
                        WarnCard {
                            if (state.isInno) {
                                // The honest fallback for any InnoSetup/repack failure is to run the
                                // real installer inside a container. FreeArc failures may be SREP.
                                val freeArc = state.engine == "unarc"
                                Text(
                                    if (freeArc) "Couldn't decode this FreeArc repack" else "Couldn't unpack this InnoSetup repack",
                                    color = MaterialTheme.colorScheme.error, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    if (freeArc)
                                        "It may use SREP or a codec this build can't decode. Install it by running " +
                                            "Setup.exe inside a Winlator container, or unpack it on a PC."
                                    else
                                        "This InnoSetup repack must be installed by running Setup.exe inside a Winlator container.",
                                    color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp,
                                )
                                Spacer(Modifier.height(8.dp))
                                RunSetupInContainer(exe = archive)
                            } else {
                                Text("Extraction failed", color = MaterialTheme.colorScheme.error, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            }
                            state.errorTail?.let {
                                Spacer(Modifier.height(6.dp))
                                Text(it.takeLast(600), color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                    UnpackPhase.CANCELLED -> {
                        Spacer(Modifier.height(4.dp))
                        SectionCard {
                            Text("Cancelled", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

/**
 * "Run Setup.exe in a container" fallback for InnoSetup repacks 7-Zip can't unpack. Picks the sole
 * container automatically; with several it offers a menu; with none it says so.
 */
@Composable
private fun RunSetupInContainer(exe: File) {
    val context = LocalContext.current
    val containers = remember { com.winhub.hernandez.util.ContainerExeRunner.containers(context) }
    var menu by remember { mutableStateOf(false) }

    fun launch(container: com.winhub.hernandez.container.Container) {
        val err = com.winhub.hernandez.util.ContainerExeRunner.run(context, container, exe)
        if (err != null) android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_LONG).show()
    }

    when {
        containers.isEmpty() -> Text(
            "Create a container first, then run ${exe.name} from the File Manager.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
        )
        else -> Box {
            Button(onClick = { if (containers.size == 1) launch(containers.first()) else menu = true }) {
                Text("Run ${exe.name} in a container")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                containers.forEach { c ->
                    DropdownMenuItem(text = { Text(c.name) }, onClick = { menu = false; launch(c) })
                }
            }
        }
    }
}

@Composable
private fun sectionTitle() = MaterialTheme.typography.labelLarge.copy(
    color = MaterialTheme.colorScheme.primary,
    fontWeight = FontWeight.SemiBold,
)

@Composable
private fun SectionCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), content = content)
    }
}

@Composable
private fun WarnCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), content = content)
        }
    }
}

private fun humanDuration(ms: Long): String = StringUtils.humanDuration(ms)
