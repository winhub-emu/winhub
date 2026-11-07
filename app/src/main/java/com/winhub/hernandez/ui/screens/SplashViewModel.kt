package com.winhub.hernandez.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.winhub.hernandez.MainActivity
import com.winhub.hernandez.ui.screens.contents.ContentsInstaller
import com.winhub.hernandez.xenvironment.ImageFs
import com.winhub.hernandez.xenvironment.ImageFsInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** One component the first-run install downloads. [driver] items go through the adrenotools importer. */
internal data class FirstRunContent(val label: String, val url: String, val driver: Boolean = false)

/**
 * Pinned from the Official catalog (winlator-contents/contents.json). All four are the
 * arm64ec/PPA-unix builds matching Proton 9 arm64ec. The setup wizard shows these as "Included".
 */
internal val FIRST_RUN_CONTENTS = listOf(
    FirstRunContent(
        label = "Proton 9 arm64ec",
        url = "https://github.com/The412Banner/Nightlies/releases/download/Proton/wine/proton-9.0-arm64ec.wcp",
    ),
    FirstRunContent(
        label = "DXVK 2.4.1 gplasync",
        url = "https://github.com/The412Banner/Nightlies/releases/download/Dxvk-gplasync-arm64ec/dxvk-gplasync-arm64ec-2.4.1-1.wcp",
    ),
    FirstRunContent(
        label = "FEXCore 2610 PPA unix",
        url = "https://github.com/The412Banner/Nightlies/releases/download/FexCore/FEXCore-2610-PPA-unix.wcp",
    ),
    FirstRunContent(
        label = "VKD3D 3.0.1",
        url = "https://github.com/The412Banner/Nightlies/releases/download/VKD3D-PROTON-ARM/vkd3d-proton-arm64ec-3.0.1.wcp",
    ),
)

class SplashViewModel(app: Application) : AndroidViewModel(app) {

    private val _isInstalling = MutableStateFlow(false)
    val isInstalling: StateFlow<Boolean> = _isInstalling

    /** True while the first-run wizard owns the screen; the install has not started yet. */
    private val _needsSetup = MutableStateFlow(false)
    val needsSetup: StateFlow<Boolean> = _needsSetup

    /** Overall setup progress: one equal slice for ImageFS, then one per component. */
    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress

    /** 0 = ImageFS, 1..n = the component at that index, n + 1 = finished. */
    private val _step = MutableStateFlow(0)
    val step: StateFlow<Int> = _step

    /** Rows of the setup list: ImageFS, the pinned components, then whatever extras were chosen. */
    private val _stepLabels = MutableStateFlow(DEFAULT_LABELS)
    val stepLabels: StateFlow<List<String>> = _stepLabels

    private val _status = MutableStateFlow("Preparing system files")
    val status: StateFlow<String> = _status

    /** True when at least one component failed; setup still completes and Proceed is offered. */
    private val _degraded = MutableStateFlow(false)
    val degraded: StateFlow<Boolean> = _degraded

    /** True once installation reaches 100% — show the Proceed button. */
    private val _showProceed = MutableStateFlow(false)
    val showProceed: StateFlow<Boolean> = _showProceed

    private var extras: List<FirstRunContent> = emptyList()

    /**
     * Check whether the system image needs (re)installation.
     * Returns true if the wizard or an install was triggered; the caller should hold back its
     * permission prompts until the user taps Proceed.
     */
    fun installIfNeeded(activity: MainActivity): Boolean {
        if (_isInstalling.value || _needsSetup.value) return true   // already running

        val imageFs = ImageFs.find(activity)
        if (imageFs.isValid && imageFs.version >= ImageFsInstaller.LATEST_VERSION) {
            // imagefs is current, so no re-extract — but the frame-gen layers and ffmpeg-8 ship as
            // APK assets alongside it, and an app update can carry newer builds of them. Without
            // this they would only ever land on a full re-extract, so a bundled layer update
            // silently never reached the device. Off the main thread: this can copy several MB.
            viewModelScope.launch(Dispatchers.IO) {
                ImageFsInstaller.stageBundledComponents(getApplication(), imageFs)
            }
            return false
        }

        // A brand-new install asks the user how they want WinHub set up before anything downloads.
        // An existing install that only needs a newer ImageFS keeps the old straight-through path.
        if (!imageFs.isValid && !setupDone()) {
            _needsSetup.value = true
            return true
        }

        beginInstall(activity)
        return true
    }

    /** The wizard's last step: remember it ran, then install the pinned components plus [chosen]. */
    internal fun finishSetup(activity: MainActivity, chosen: List<FirstRunContent>) {
        if (!_needsSetup.value) return
        PreferenceManager.getDefaultSharedPreferences(getApplication())
            .edit().putBoolean(KEY_SETUP_DONE, true).apply()
        extras = chosen
        _needsSetup.value = false
        beginInstall(activity)
    }

    private fun setupDone(): Boolean =
        PreferenceManager.getDefaultSharedPreferences(getApplication()).getBoolean(KEY_SETUP_DONE, false)

    private fun beginInstall(activity: MainActivity) {
        _isInstalling.value = true
        _progress.value = 0
        _step.value = 0
        _stepLabels.value = listOf("System files") + FIRST_RUN_CONTENTS.map { it.label } + extras.map { it.label }
        _status.value = "Installing system files"
        _degraded.value = false
        _showProceed.value = false

        val slices = 1 + FIRST_RUN_CONTENTS.size + extras.size
        ImageFsInstaller.installFromAssetsWithCallback(
            activity,
            { pct ->
                // ImageFS is the first slice.
                _progress.value = overall(slices, 0, pct)
            },
            {
                _progress.value = overall(slices, 1, 0)
                viewModelScope.launch { installFirstRunContents() }
            },
        )
    }

    /** Overall percent when [done] slices are finished and the current one is [pct] percent through. */
    private fun overall(slices: Int, done: Int, pct: Int): Int =
        ((done * 100 + pct.coerceIn(0, 100)) / slices).coerceIn(0, 100)

    /**
     * One-time component setup after a fresh ImageFS extract: Proton, DXVK, FEXCore and VKD3D,
     * pinned to the exact Official-catalog builds, then any extras the user ticked in the wizard,
     * installed sequentially behind the same overlay. Failures are counted, not fatal — the user
     * can retry from Contents later.
     */
    private suspend fun installFirstRunContents() {
        var failures = 0
        val app = getApplication<Application>()
        val items = FIRST_RUN_CONTENTS + extras
        val slices = 1 + items.size

        items.forEachIndexed { index, item ->
            _step.value = index + 1
            _status.value = "${item.label} — starting"
            val onStatus: (String) -> Unit = { message ->
                _status.value = "${item.label} — $message"
                PERCENT.find(message)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { pct ->
                    _progress.value = overall(slices, index + 1, pct)
                }
            }
            val ok = if (item.driver) {
                ContentsInstaller.installDriverAwait(app, item.url, onStatus) != null
            } else {
                ContentsInstaller.installAwait(app, item.url, onStatus)
            }
            if (!ok) failures++
            _progress.value = overall(slices, index + 2, 0)
        }

        _step.value = items.size + 1
        _progress.value = 100
        _degraded.value = failures > 0
        _status.value =
            if (failures == 0) "Ready to play"
            else "$failures component(s) failed — retry from Contents"
        _showProceed.value = true
    }

    /**
     * Settings → Reinstall ImageFS. Reuses the same overlay, but deliberately does NOT reinstall
     * the components: those are first-open only.
     */
    fun reinstallImageFs(activity: MainActivity) {
        if (_isInstalling.value) return
        _isInstalling.value = true
        _progress.value = 0
        _step.value = 0
        _stepLabels.value = DEFAULT_LABELS
        _status.value = "Reinstalling system files"
        _degraded.value = false
        _showProceed.value = false

        ImageFsInstaller.installFromAssetsWithCallback(
            activity,
            { pct -> _progress.value = pct.coerceIn(0, 100) },
            {
                _progress.value = 100
                _step.value = FIRST_RUN_CONTENTS.size + 1
                _status.value = "System files reinstalled"
                _showProceed.value = true
            },
        )
    }

    /** Called when user taps Proceed; hides the splash overlay. */
    fun dismissSplash() {
        _showProceed.value = false
        _isInstalling.value = false
    }

    private companion object {
        val PERCENT = Regex("(\\d+)%")
        const val KEY_SETUP_DONE = "first_run_setup_done"
        val DEFAULT_LABELS = listOf("System files") + FIRST_RUN_CONTENTS.map { it.label }
    }
}
