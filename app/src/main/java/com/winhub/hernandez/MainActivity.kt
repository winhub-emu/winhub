package com.winhub.hernandez

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import com.winhub.hernandez.ui.screens.OutlinedAlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Surface
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import com.winhub.hernandez.ui.components.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.winhub.hernandez.core.UpdateManager
import androidx.compose.runtime.rememberCoroutineScope
import com.winhub.hernandez.ui.LocalTopBarActions
import com.winhub.hernandez.ui.LocalTopBarOverlayInset
import com.winhub.hernandez.ui.LocalTopBarTransparent
import com.winhub.hernandez.ui.topBarActionsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.winhub.hernandez.BuildConfig
import com.winhub.hernandez.core.ImageUtils
import com.winhub.hernandez.core.PreloaderDialog
import com.winhub.hernandez.core.WineThemeManager
import com.winhub.hernandez.container.ContainerManager
import com.winhub.hernandez.store.AmazonMainActivity
import com.winhub.hernandez.store.xbox.XboxMainActivity
import com.winhub.hernandez.store.EpicMainActivity
import com.winhub.hernandez.store.GogMainActivity
import com.winhub.hernandez.store.SteamMainActivity
import com.winhub.hernandez.ui.AccountUiBus
import com.winhub.hernandez.ui.AppDrawerContent
import com.winhub.hernandez.ui.AppNavGraph
import com.winhub.hernandez.ui.AppTopBar
import com.winhub.hernandez.ui.PreloaderOverlay
import com.winhub.hernandez.ui.Screen
import com.winhub.hernandez.ui.screens.SplashScreen
import com.winhub.hernandez.ui.screens.SplashViewModel
import com.winhub.hernandez.ui.theme.AppThemeState
import com.winhub.hernandez.ui.theme.WinlatorTheme
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    companion object {
        const val PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE: Byte = 1
        const val OPEN_FILE_REQUEST_CODE: Byte = 2
        const val OPEN_DIRECTORY_REQUEST_CODE: Byte = 4
        const val OPEN_IMAGE_REQUEST_CODE: Byte = 5

        /** String extra: a Screen route to open (e.g. Screen.Games.route). Used by
         *  the store activities' "Open Shortcuts" action to deep-link back here. */
        const val EXTRA_OPEN_SCREEN = "open_screen"
        @JvmField val CONTAINER_PATTERN_COMPRESSION_LEVEL: Byte = 9
        @JvmField var PACKAGE_NAME: String = ""
    }

    @JvmField val preloaderDialog: PreloaderDialog = PreloaderDialog(this)
    lateinit var containerManager: ContainerManager
        private set

    private val splashViewModel: SplashViewModel by lazy {
        ViewModelProvider(this)[SplashViewModel::class.java]
    }

    // Holds the OS cold-start splash on screen only until the Compose UI is about to draw its first
    // frame. Not held for the imagefs install — that has its own in-app SplashScreen surface.
    @Volatile
    private var contentReady = false

    private val showAllFilesDialog = mutableStateOf(false)
    private val showAboutDialog = mutableStateOf(false)

    // Route requested via EXTRA_OPEN_SCREEN on a relaunch (onNewIntent); consumed
    // by AppShell, which navigates to it and clears it.
    private val pendingRoute = mutableStateOf<String?>(null)

    // ---- Settings-side Controller Test (Input Controls screen) input fork ----
    // While the at-rest controller-test dialog is open in TEST mode a game controller's key/axis events
    // are forked into controllerTestController — a throwaway snapshot that only drives the visualizer —
    // and CONSUMED at the dispatch chokepoints so gamepad presses can't navigate the Compose UI. The
    // gate is read DIRECTLY from ControllerTestBus.active (a @Volatile set SYNCHRONOUSLY when the dialog
    // shows, via a SideEffect — not a late LaunchedEffect/callback), AND'd with !controllerTestPaused so
    // a background can't leave it latched. This is the fix for the "doesn't react + leaks until rotate"
    // bug: the very first press after opening is already gated on the immediate value.
    @Volatile private var controllerTestPaused = false
    private val controllerTestController = com.winhub.hernandez.inputcontrols.ExternalController()
    private var controllerTestGuideDown = false
    private var lastControllerTestAxisLogMs = 0L

    private fun settingsTestArmed(): Boolean =
        com.winhub.hernandez.ui.controllertest.ControllerTestBus.active && !controllerTestPaused
    private var controllerTestLastDeviceId = -1

    // Steam Controller support in the at-rest test dialog: SDL runs only while the dialog is open (and
    // the setting is on), feeding the same snapshot as an Android pad. A Steam Controller has no Android
    // gamepad device, so without this the test never sees it.
    private var settingsSteamBackend: com.winhub.hernandez.inputcontrols.SteamControllerBackend? = null
    private var settingsSteamPads = 0

    private val openImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val bitmap = result.data?.data?.let {
                ImageUtils.getBitmapFromUri(this, it, 1280)
            } ?: return@registerForActivityResult
            val file = WineThemeManager.getUserWallpaperFile(this)
            ImageUtils.save(bitmap, file, Bitmap.CompressFormat.PNG, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // A fresh open of the app plays the services launch screen again; rotations and returns do not.
        if (savedInstanceState == null) com.winhub.hernandez.ui.screens.DeckBoot.shown = false
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        // Wire the AndroidX cold-start splash BEFORE super.onCreate so the OS splash bridges the gap
        // to our first Compose frame; hold it only until the UI is ready to draw (set just below).
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { !contentReady }

        PACKAGE_NAME = applicationContext.packageName
        AppThemeState.init(this)
        // Apply the user's App-orientation preference to THIS app-UI activity only (the game's
        // XServerDisplayActivity manages its own orientation and is unaffected).
        com.winhub.hernandez.core.AppOrientation.apply(this)

        val winlatorDir = File(SettingsFragment.DEFAULT_WINLATOR_PATH)
        if (!winlatorDir.exists()) winlatorDir.mkdirs()

        containerManager = ContainerManager(this)

        // Deck-only build: the legacy phone UI is retired. Every launch lands in Deck Mode, and
        // relaunch intents aimed at the old screens are ignored so two shells never stack up.
        val startRoute = Screen.Deck.route

        val willInstall = splashViewModel.installIfNeeded(this)
        if (!willInstall) {
            // Already installed — request permissions immediately
            requestAppPermissions()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                showAllFilesDialog.value = true
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
            }
        }
        // If willInstall == true: permissions are requested after user taps Proceed

        // First-run/install decision is made; let the OS splash hand off to the Compose UI.
        contentReady = true

        // Settings-side Controller Test: the Input Controls screen's test dialog arms/disarms the input
        // fork through this bus (so this Activity consumes gamepad events instead of navigating the UI),
        // and asks us to natively rumble the live pad for "Identify".
        // The gate itself is ControllerTestBus.active (read directly in dispatch). This callback only
        // clears the throwaway controller's stale state when the dialog opens, and drops the snapshot
        // when it closes.
        com.winhub.hernandez.ui.controllertest.ControllerTestBus.onActiveChanged =
            com.winhub.hernandez.ui.controllertest.ControllerTestBus.ActiveCallback { active ->
                if (active) {
                    controllerTestController.state.reset()
                    controllerTestController.remappedState.reset()
                    controllerTestGuideDown = false
                    startSettingsSteamController()
                } else {
                    stopSettingsSteamController()
                    com.winhub.hernandez.ui.controllertest.ControllerTestBus.setSnapshot(null)
                }
            }
        com.winhub.hernandez.ui.controllertest.ControllerTestBus.onIdentify =
            Runnable { settingsControllerIdentify() }

        setContent {
            WinlatorTheme {
                val isInstalling by splashViewModel.isInstalling.collectAsState()
                val installProgress by splashViewModel.progress.collectAsState()
                val installStatus by splashViewModel.status.collectAsState()
                val installStep by splashViewModel.step.collectAsState()
                val installDegraded by splashViewModel.degraded.collectAsState()
                val showProceed by splashViewModel.showProceed.collectAsState()
                val installLabels by splashViewModel.stepLabels.collectAsState()
                val needsSetup by splashViewModel.needsSetup.collectAsState()

                Box(modifier = Modifier.fillMaxSize()) {
                    AppShell(
                        startRoute = startRoute,
                        pendingRoute = pendingRoute.value,
                        onPendingRouteConsumed = { pendingRoute.value = null },
                        showAllFilesDialog = showAllFilesDialog.value,
                        showAboutDialog = showAboutDialog.value,
                        onDismissAllFilesDialog = { showAllFilesDialog.value = false },
                        onConfirmAllFilesDialog = {
                            showAllFilesDialog.value = false
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:$packageName")
                            startActivity(intent)
                        },
                        onDismissAboutDialog = { showAboutDialog.value = false },
                        onAboutRequested = { showAboutDialog.value = true },
                        onLaunchStore = { screen -> launchStore(screen) },
                    )

                    // Resume a mid-flight component installer (Phase 3b) after the app restarts.
                    // On completion/discard it routes back to Games (via pendingRoute, same one-shot
                    // channel the store deep-link uses) so the user isn't stranded on the resume dialog.
                    com.winhub.hernandez.ui.screens.ComponentInstallResume(
                        onNavigateToGames = { pendingRoute.value = Screen.Deck.route },
                    )

                    // First open on a fresh install: the user picks layout, theme, extras and Social Hub
                    // before anything downloads. Finishing hands off to the install overlay below.
                    if (needsSetup) {
                        com.winhub.hernandez.ui.screens.FirstRunSetup(
                            onInstall = { extras -> splashViewModel.finishSetup(this@MainActivity, extras) },
                        )
                    }

                    if (isInstalling) {
                        SplashScreen(
                            progress = installProgress,
                            status = installStatus,
                            step = installStep,
                            degraded = installDegraded,
                            stepLabels = installLabels,
                            showProceed = showProceed,
                            onProceed = {
                                splashViewModel.dismissSplash()
                                requestAppPermissions()
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                                    !Environment.isExternalStorageManager()
                                ) {
                                    showAllFilesDialog.value = true
                                }
                                if (Build.VERSION.SDK_INT >= 33 &&
                                    ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    requestPermissions(
                                        arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0
                                    )
                                }
                            },
                        )
                    }

                    // Compose-based preloader overlay — replaces XML PreloaderDialog
                    PreloaderOverlay()
                }
            }
        }
    }

    /** Settings → Reinstall ImageFS: runs behind the same WinHub setup overlay. */
    internal fun reinstallImageFs() = splashViewModel.reinstallImageFs(this)

    private fun launchStore(screen: Screen) {
        val cls = when (screen) {
            Screen.Gog    -> GogMainActivity::class.java
            Screen.Epic   -> EpicMainActivity::class.java
            Screen.Amazon -> AmazonMainActivity::class.java
            Screen.Xbox   -> XboxMainActivity::class.java
            Screen.Steam  -> SteamMainActivity::class.java
            else          -> return
        }
        startActivity(Intent(this, cls))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Install runs independently now; nothing to do after storage permission result.
    }

    private fun requestAppPermissions() {
        val hasWrite = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        val hasRead = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        val storageReady = hasWrite && hasRead || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        if (storageReady) return  // Already granted; install was already started separately.

        requestPermissions(
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE),
            PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE.toInt(),
        )
    }

    /** Called by DownloadProgressDialog after a download to re-request permissions if needed. */
    fun doPermissionsFlow() {
        requestAppPermissions()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !android.os.Environment.isExternalStorageManager()) {
            showAllFilesDialog.value = true
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Deep-link from the store activities (CLEAR_TOP|SINGLE_TOP relaunch).
        validRouteOrNull(intent.getStringExtra(EXTRA_OPEN_SCREEN))?.let {
            pendingRoute.value = it
        }
    }

    override fun onDestroy() {
        stopSettingsSteamController()
        super.onDestroy()
    }

    private fun startSettingsSteamController() {
        if (settingsSteamBackend != null) return
        if (!com.winhub.hernandez.ui.components.GlobalControllerPrefs.isSteamControllerEnabled(this)) return
        val backend = com.winhub.hernandez.inputcontrols.SteamControllerBackend(
            this, com.winhub.hernandez.inputcontrols.SteamControllerBackend.TRACKPAD_MOUSE_OFF,
            com.winhub.hernandez.ui.components.GlobalControllerPrefs.getSteamPaddleBindings(this),
            object : com.winhub.hernandez.inputcontrols.SteamControllerBackend.Listener {
                override fun onSteamPadConnected(pad: com.winhub.hernandez.inputcontrols.ExternalController) {
                    settingsSteamPads++
                }

                override fun onSteamPadDisconnected(pad: com.winhub.hernandez.inputcontrols.ExternalController) {
                    settingsSteamPads = (settingsSteamPads - 1).coerceAtLeast(0)
                }

                override fun onSteamPadState(
                    pad: com.winhub.hernandez.inputcontrols.ExternalController,
                    guideDown: Boolean,
                    quickAccessDown: Boolean,
                    pressedKeyCodes: IntArray,
                ) {
                    if (!settingsTestArmed()) return
                    controllerTestController.state.copy(pad.state)
                    controllerTestGuideDown = guideDown
                    controllerTestLastDeviceId = pad.deviceId
                    val st = controllerTestController.state
                    com.winhub.hernandez.ui.controllertest.ControllerTestBus.setSnapshot(
                        com.winhub.hernandez.ui.controllertest.ControllerTestSnapshot(
                            st.buttons.toInt() and 0xFFFF,
                            st.dpad[0], st.dpad[1], st.dpad[2], st.dpad[3],
                            st.thumbLX, st.thumbLY, st.thumbRX, st.thumbRY,
                            st.triggerL, st.triggerR,
                            guideDown,
                            pad.deviceId,
                            pad.name ?: "Steam Controller",
                            com.winhub.hernandez.ui.controllertest.PadArt.STEAM.ordinal,
                            -1,
                            true,
                            quickAccessDown
                        )
                    )
                }

                override fun onSteamPadBinding(binding: com.winhub.hernandez.inputcontrols.Binding, down: Boolean) {}
                override fun onSteamPadMouseMove(dx: Int, dy: Int) {}
                override fun onSteamPadMouseButton(secondary: Boolean, down: Boolean) {}
            }
        )
        if (backend.start()) settingsSteamBackend = backend
    }

    private fun stopSettingsSteamController() {
        val backend = settingsSteamBackend ?: return
        settingsSteamBackend = null
        settingsSteamPads = 0
        backend.stop()
    }

    /** While SDL owns a Steam Controller in the test dialog, whatever Android still reports for it (its
     *  keyboard/mouse mode) is the same pad: consume it so it can't navigate the UI or feed the fork. */
    private fun isSettingsSteamShadowEvent(device: android.view.InputDevice?): Boolean =
        settingsSteamBackend != null && settingsSteamPads > 0 &&
            device?.vendorId == com.winhub.hernandez.inputcontrols.SteamControllerBackend.VALVE_VENDOR_ID

    override fun onPause() {
        super.onPause()
        // Pause (not clear) the fork across a background; the bus flag stays set by the open dialog so
        // onResume re-arms without needing the dialog to recompose.
        controllerTestPaused = true
    }

    override fun onStop() {
        super.onStop()
        controllerTestPaused = true
    }

    override fun onResume() {
        super.onResume()
        com.winhub.hernandez.core.MusicPlayer.setInGame(false)
        controllerTestPaused = false
        // A game may still be playing on the TV. Tapping the launcher brings THIS task forward — the
        // companion screen is in a task of its own, deliberately, so the system has no reason to prefer
        // it — and the user was left looking at the games list with nothing to say a session was live.
        // Hand the screen over to the companion, which is where "Send input back to the TV" and "End the
        // game" are, and which sends the controller back to the TV as it comes up.
        //
        // The companion answers for itself whether there is anything to come back to, from the state the
        // SESSION maintains: no live TV session and this is a no-op, so an ordinary handheld session (or
        // no session at all) lands exactly where it always did. Nothing here reaches the session on the
        // TV: the game keeps playing, untouched, whichever way this goes.
        com.winhub.hernandez.display.TvCompanionActivity.resumeForLiveSession(this)
    }

    // ---- Settings-side Controller Test input fork ----
    // Gate = ControllerTestBus.active (armed SYNCHRONOUSLY by the dialog's SideEffect) AND not paused.
    // When CLOSED both overrides just call super. When OPEN in TEST mode, a game-controller event drives
    // ONLY the throwaway visualizer snapshot and is CONSUMED so it can't navigate the app UI.

    override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        if (isSettingsSteamShadowEvent(event.device)) return true
        if (settingsTestArmed() && isControllerTestMotionEvent(event)) {
            controllerTestFeedMotionEvent(event)
            return true
        }
        // A pad's D-pad is very often an AXIS_HAT_* MOTION event, and the analog stick is always
        // motion — neither ever becomes a key event on its own. Without this bridge the whole
        // launcher is touch-only for a controller (arrows did nothing at all). Turn stick/hat
        // motion into synthetic D-pad presses so Compose's focus walker can move.
        if (handlePadAxisNavigation(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    private val padAxisHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var padAxisHeldKey = 0
    // L2/R2 as analog triggers become key presses (edge-triggered) so the Deck can use them as
    // sub-tab buttons; pads that also send real L2/R2 keys are left to those keys.
    private var padL2Down = false
    private var padR2Down = false
    private var padTriggerKeySeen = false
    private var padAxisLastEventMs = 0L
    private val padAxisRepeater = object : Runnable {
        override fun run() {
            val stale = android.os.SystemClock.uptimeMillis() - padAxisLastEventMs > 400L
            // A dialog/popup owns a window of its own: never keep injecting D-pad presses into the
            // Activity underneath it (the focus owner there has no target and used to throw).
            if (!hasWindowFocus() || padAxisHeldKey == 0 || stale) {
                padAxisHeldKey = 0
                return
            }
            dispatchSyntheticKey(padAxisHeldKey)
            padAxisHandler.postDelayed(this, 110L)
        }
    }

    /**
     * An analog trigger pulled past the threshold. The synthetic key goes straight to super, so it never
     * passed the Deck's hard-key hook above: sub-tab switching (L2/R2) was dead on pads with analog triggers.
     */
    private fun dispatchSyntheticTrigger(keyCode: Int) {
        if (com.winhub.hernandez.ui.screens.DeckHardKeys.handler?.invoke(keyCode) == true) return
        dispatchSyntheticKey(keyCode)
    }

    /** Fire one D-pad press straight at the window (bypasses the A/B rewrite in dispatchKeyEvent). */
    private fun dispatchSyntheticKey(keyCode: Int) {
        val now = android.os.SystemClock.uptimeMillis()
        super.dispatchKeyEvent(
            android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, keyCode, 0)
        )
        super.dispatchKeyEvent(
            android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP, keyCode, 0)
        )
    }

    /**
     * Edge-triggered stick/hat navigation: one press when a direction is entered, then auto-repeat
     * while it is held. Deadzone is generous so a drifting stick never walks the UI on its own.
     */
    private fun handlePadAxisNavigation(event: android.view.MotionEvent): Boolean {
        val device = event.device ?: return false
        if (!com.winhub.hernandez.inputcontrols.ExternalController.isGameController(device)) return false
        val src = event.source
        val joystick = (src and android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK ||
            (src and android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD
        if (!joystick) return false
        // While a Compose Dialog/Popup is up this Activity's Compose tree has no focus target;
        // injecting a synthetic direction would walk (or crash) the screen behind it.
        if (!hasWindowFocus()) return false

        val l2 = maxOf(
            event.getAxisValue(android.view.MotionEvent.AXIS_LTRIGGER),
            event.getAxisValue(android.view.MotionEvent.AXIS_BRAKE),
        ) > 0.6f
        val r2 = maxOf(
            event.getAxisValue(android.view.MotionEvent.AXIS_RTRIGGER),
            event.getAxisValue(android.view.MotionEvent.AXIS_GAS),
        ) > 0.6f
        if (!padTriggerKeySeen) {
            if (l2 && !padL2Down) dispatchSyntheticTrigger(android.view.KeyEvent.KEYCODE_BUTTON_L2)
            if (r2 && !padR2Down) dispatchSyntheticTrigger(android.view.KeyEvent.KEYCODE_BUTTON_R2)
        }
        padL2Down = l2
        padR2Down = r2

        var x = event.getAxisValue(android.view.MotionEvent.AXIS_HAT_X)
        var y = event.getAxisValue(android.view.MotionEvent.AXIS_HAT_Y)
        if (x == 0f && y == 0f) {
            x = event.getAxisValue(android.view.MotionEvent.AXIS_X)
            y = event.getAxisValue(android.view.MotionEvent.AXIS_Y)
        }
        val dz = 0.6f
        val key = when {
            x <= -dz && kotlin.math.abs(x) >= kotlin.math.abs(y) -> android.view.KeyEvent.KEYCODE_DPAD_LEFT
            x >= dz && kotlin.math.abs(x) >= kotlin.math.abs(y) -> android.view.KeyEvent.KEYCODE_DPAD_RIGHT
            y <= -dz -> android.view.KeyEvent.KEYCODE_DPAD_UP
            y >= dz -> android.view.KeyEvent.KEYCODE_DPAD_DOWN
            else -> 0
        }
        padAxisLastEventMs = android.os.SystemClock.uptimeMillis()
        if (key == padAxisHeldKey) return true
        padAxisHeldKey = key
        padAxisHandler.removeCallbacks(padAxisRepeater)
        if (key != 0) {
            dispatchSyntheticKey(key)
            padAxisHandler.postDelayed(padAxisRepeater, 380L)
        }
        return true
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (isSettingsSteamShadowEvent(event.device)) return true
        if (settingsTestArmed() &&
            com.winhub.hernandez.inputcontrols.ExternalController.isGameController(event.device)) {
            controllerTestFeedKeyEvent(event)
            return true
        }
        if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
            com.winhub.hernandez.ui.screens.DeckHardKeys.handler?.invoke(event.keyCode) == true) return true
        // Compose only activates components on DPAD_CENTER/ENTER, and never reads a pad's B as
        // back — rewrite the face buttons for the launcher so the whole UI is walkable. The Wine
        // session runs in XServerDisplayActivity, which keeps the raw key codes.
        if (event.keyCode == android.view.KeyEvent.KEYCODE_BUTTON_L2 ||
            event.keyCode == android.view.KeyEvent.KEYCODE_BUTTON_R2) padTriggerKeySeen = true
        val keyCode = when (event.keyCode) {
            android.view.KeyEvent.KEYCODE_BUTTON_A -> android.view.KeyEvent.KEYCODE_DPAD_CENTER
            android.view.KeyEvent.KEYCODE_BUTTON_B -> android.view.KeyEvent.KEYCODE_BACK
            else -> return super.dispatchKeyEvent(event)
        }
        val mapped = android.view.KeyEvent(
            event.downTime,
            event.eventTime,
            event.action,
            keyCode,
            event.repeatCount,
            event.metaState,
            event.deviceId,
            event.scanCode,
            event.flags,
            event.source
        )
        return super.dispatchKeyEvent(mapped)
    }

    private fun isControllerTestMotionEvent(event: android.view.MotionEvent): Boolean {
        val src = event.source
        val joystickish =
            (src and android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK ||
            (src and android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD
        return joystickish && com.winhub.hernandez.inputcontrols.ExternalController.isGameController(event.device)
    }

    private fun controllerTestFeedMotionEvent(event: android.view.MotionEvent) {
        if (com.winhub.hernandez.inputcontrols.ExternalController.isJoystickDevice(event)) {
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastControllerTestAxisLogMs > 1000L) {
                lastControllerTestAxisLogMs = now
                android.util.Log.d(
                    "ControllerTest",
                    "settings axis dispatch reached src=" + event.source + " dev=" + event.deviceId
                )
            }
        }
        controllerTestController.updateStateFromMotionEvent(event)
        controllerTestPublishSnapshot(event.device)
    }

    private fun controllerTestFeedKeyEvent(event: android.view.KeyEvent) {
        if (event.repeatCount == 0) controllerTestController.updateStateFromKeyEvent(event)
        val kc = event.keyCode
        if (kc == android.view.KeyEvent.KEYCODE_BUTTON_MODE || kc == android.view.KeyEvent.KEYCODE_HOME) {
            controllerTestGuideDown = event.action == android.view.KeyEvent.ACTION_DOWN
        }
        controllerTestPublishSnapshot(event.device)
    }

    private fun controllerTestPublishSnapshot(device: android.view.InputDevice?) {
        val st = controllerTestController.state
        var battery = -1
        if (device != null && Build.VERSION.SDK_INT >= 29) {
            try {
                val bs = device.batteryState
                if (bs != null && bs.isPresent) {
                    val cap = bs.capacity
                    if (cap >= 0f) battery = Math.round(cap * 100f)
                }
            } catch (_: Throwable) { }
        }
        var hasVibrator = false
        if (device != null) {
            val vib = device.vibrator
            hasVibrator = vib != null && vib.hasVibrator()
        }
        controllerTestLastDeviceId = device?.id ?: -1
        com.winhub.hernandez.ui.controllertest.ControllerTestBus.setSnapshot(
            com.winhub.hernandez.ui.controllertest.ControllerTestSnapshot(
                st.buttons.toInt() and 0xFFFF,
                st.dpad[0], st.dpad[1], st.dpad[2], st.dpad[3],
                st.thumbLX, st.thumbLY, st.thumbRX, st.thumbRY,
                st.triggerL, st.triggerR,
                controllerTestGuideDown,
                device?.id ?: -1,
                device?.name ?: "",
                com.winhub.hernandez.ui.controllertest.classifyPadArt(device).ordinal,
                battery,
                hasVibrator
            )
        )
    }

    /** Native "Identify" — rumble the last pad the visualizer saw, via VibratorManager (independent
     *  motors, API 31+) or the single vibrator otherwise. No game / WinHandler here. */
    private fun settingsControllerIdentify() {
        val id = controllerTestLastDeviceId
        // Steam Controller (SDL, synthetic deviceId): rumble through SDL.
        if (id <= com.winhub.hernandez.inputcontrols.SteamControllerBackend.DEVICE_ID_BASE) {
            settingsSteamBackend?.rumble(id, 48000, 32000, 420)
            return
        }
        if (id < 0) return
        val device = android.view.InputDevice.getDevice(id) ?: return
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val vm = device.vibratorManager
                val ids = vm?.vibratorIds
                if (vm != null && ids != null && ids.isNotEmpty()) {
                    val combo = android.os.CombinedVibration.startParallel()
                    for (vid in ids) combo.addVibrator(vid, android.os.VibrationEffect.createOneShot(420L, 200))
                    vm.vibrate(combo.combine())
                    return
                }
            }
            val v = device.vibrator
            if (v != null && v.hasVibrator()) {
                v.vibrate(android.os.VibrationEffect.createOneShot(420L, 200))
            }
        } catch (_: Throwable) { }
    }

    /** Only accepts known drawer routes so a bad extra can't crash navigation. */
    private fun validRouteOrNull(route: String?): String? =
        route?.takeIf { r -> Screen.drawerItems.any { it.route == r } }

    private fun menuItemIdToRoute(itemId: Int): String? = when (itemId) {
        R.id.main_menu_containers -> Screen.Containers.route
        R.id.main_menu_shortcuts  -> Screen.Games.route
        R.id.main_menu_contents   -> Screen.Contents.route
        R.id.main_menu_input_controls -> Screen.InputControls.route
        R.id.main_menu_adrenotools_gpu_drivers -> Screen.AdrenoTools.route
        R.id.main_menu_settings   -> Screen.Settings.route
        else -> null
    }
}

@Composable
private fun AppShell(
    startRoute: String,
    pendingRoute: String?,
    onPendingRouteConsumed: () -> Unit,
    showAllFilesDialog: Boolean,
    showAboutDialog: Boolean,
    onDismissAllFilesDialog: () -> Unit,
    onConfirmAllFilesDialog: () -> Unit,
    onDismissAboutDialog: () -> Unit,
    onAboutRequested: () -> Unit,
    onLaunchStore: (Screen) -> Unit,
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val topBarActionsState = remember { topBarActionsState() }
    val topBarTransparentState = remember { mutableStateOf(false) }

    val backstackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backstackEntry?.destination?.route ?: startRoute
    // Back (B on a pad) on the bottom screen has nothing to go up to: swallow it instead of finishing the app.
    // Registered before any screen's own handler, so those still win while they have something to close.
    BackHandler(enabled = backstackEntry != null && navController.previousBackStackEntry == null) { }

    // Big Picture is a full-bleed couch/TV launcher: no top bar, no drawer gestures, no scaffold
    // content padding (it draws its own immersive layout).
    // Big Picture now renders the landscape "games wall", which draws its OWN rail header + nav rail +
    // footer, so it gets the chrome-free full-bleed treatment. The Games route is the normal phone-grid
    // library again (top bar + drawer), so it is NOT full-bleed.
    val isBigPicture = currentRoute == Screen.BigPicture.route
    // Deck Mode is the same chrome-free treatment: it draws its own rail and header.
    val isFullBleed = isBigPicture || currentRoute == Screen.Deck.route

    // In-app update banner: only when a newer stable exists, notify is on, and
    // this version wasn't skipped.
    var bannerUpdate by remember { mutableStateOf<UpdateManager.UpdateInfo?>(null) }
    var bannerDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        UpdateManager.check(context) { info ->
            (context as? MainActivity)?.runOnUiThread {
                if (info != null && info.isNewer &&
                    UpdateManager.isNotifyEnabled(context) &&
                    info.versionCode != UpdateManager.skippedVersionCode(context)
                ) {
                    bannerUpdate = info
                }
            }
        }
    }

    // Navigate to a route requested by a relaunch intent (store "Open Shortcuts").
    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            navController.navigate(pendingRoute) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            onPendingRouteConsumed()
        }
    }

    // Clear top bar actions on navigation so stale actions from a previous screen don't persist.
    // Screens that need actions re-set them via SideEffect on each recomposition.
    // Also re-read the optional signed-in account so the ☰→avatar swap / drawer header reflect a login
    // or logout that happened on the screen we're returning from.
    LaunchedEffect(currentRoute) {
        topBarActionsState.value = {}
        AccountUiBus.refresh(context)
    }
    // Reactive mirror of the signed-in account (null = logged out / anonymous UX unchanged).
    val account = AccountUiBus.account

    val screenTitle = when {
        currentRoute.startsWith("container_detail") -> {
            val id = backstackEntry?.arguments?.getInt("id") ?: -1
            when {
                id == com.winhub.hernandez.ui.screens.ContainerDetailViewModel.EDIT_DEFAULTS_ID ->
                    context.getString(R.string.new_container_defaults)
                id > 0 -> context.getString(R.string.edit_container)
                else -> context.getString(R.string.new_container)
            }
        }
        else -> Screen.drawerItems.firstOrNull { it.route == currentRoute }?.label ?: "Winlator"
    }

    // The Games tab's XMB view asks for a see-through top bar; the screen is then laid out under the
    // bar so its backdrop runs to the top. Games route only, and not while the update banner shows
    // (it sits between the bar and the screen).
    val barOverlay = currentRoute == Screen.Games.route && topBarTransparentState.value &&
        !isFullBleed && !(bannerUpdate != null && !bannerDismissed)

    CompositionLocalProvider(LocalTopBarActions provides topBarActionsState, LocalTopBarTransparent provides topBarTransparentState) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !currentRoute.startsWith("container_detail") && !isFullBleed,
        drawerContent = {
            // Only compose the drawer while it is actually open. A closed ModalNavigationDrawer
            // keeps its content laid out off-screen, where the D-pad focus search still finds it
            // (Deck rail -> Left x2 -> A used to land on a legacy route). With no drawer content
            // there is nothing off-screen to focus, so the old Fit-UI entry points are unreachable.
            if (drawerState.isOpen) AppDrawerContent(
                currentRoute = currentRoute,
                account = account,
                onNavigate = { screen ->
                    scope.launch { drawerState.close() }
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onLaunchStore = { screen ->
                    scope.launch { drawerState.close() }
                    onLaunchStore(screen)
                },
                onAbout = {
                    scope.launch { drawerState.close() }
                    onAboutRequested()
                },
                // The My-account sheet lives on the Shortcuts screen; land there, then ask it to open.
                onMyAccount = {
                    scope.launch { drawerState.close() }
                    navController.navigate(Screen.Games.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                    AccountUiBus.requestMyAccount()
                },
            )
        },
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                if (!isFullBleed) {
                    AppTopBar(
                        title = screenTitle,
                        showBack = false,
                        // Signed-in + has a picture → the ☰ becomes their avatar (still opens the drawer).
                        // Versioned URL so a live picture change refreshes the swap in lockstep with the drawer.
                        avatarUrl = account?.displayAvatarUrl,
                        onNavClick = {
                            scope.launch {
                                if (drawerState.isOpen) drawerState.close() else drawerState.open()
                            }
                        },
                        // Steam connection status pill, right after the "Games" title (Games screen only).
                        // Self-gates to signed-in users; tap when offline to retry. See SteamConnectionPill.
                        titleTrailing = if (currentRoute == Screen.Games.route) {
                            { com.winhub.hernandez.store.SteamConnectionPill() }
                        } else null,
                        transparent = barOverlay,
                        actions = topBarActionsState.value,
                    )
                }
            },
        ) { innerPadding ->
            val layoutDir = LocalLayoutDirection.current
            val contentPadding = when {
                isFullBleed -> PaddingValues(0.dp)
                // Drawn under the see-through bar: every inset except the top.
                barOverlay -> PaddingValues(
                    start = innerPadding.calculateStartPadding(layoutDir),
                    end = innerPadding.calculateEndPadding(layoutDir),
                    bottom = innerPadding.calculateBottomPadding(),
                )
                else -> innerPadding
            }
            CompositionLocalProvider(LocalTopBarOverlayInset provides if (barOverlay) innerPadding.calculateTopPadding() else 0.dp) {
            Column(modifier = Modifier.padding(contentPadding)) {
                val upd = bannerUpdate
                if (upd != null && !bannerDismissed && !isFullBleed) {
                    UpdateBanner(
                        versionName = upd.versionName,
                        onUpdate = {
                            (context as? MainActivity)?.let { UpdateManager.downloadAndInstall(it, upd) {} }
                        },
                        onDismiss = {
                            bannerDismissed = true
                            UpdateManager.skipVersion(context, upd.versionCode)
                        },
                    )
                }
                AppNavGraph(
                    navController = navController,
                    startRoute = startRoute,
                    modifier = Modifier.weight(1f),
                )
                // App-wide minimized progress pill for a running archive unpack. Renders nothing when
                // idle; sits below the nav content so it floats over every screen. Hidden in Big
                // Picture (fullscreen) mode.
                if (!isFullBleed) {
                    com.winhub.hernandez.ui.UnpackProgressPill()
                }
            }
            } // end LocalTopBarOverlayInset
        }
    }
    } // end CompositionLocalProvider

    if (showAllFilesDialog) {
        AllFilesAccessDialog(
            onConfirm = onConfirmAllFilesDialog,
            onDismiss = onDismissAllFilesDialog,
        )
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = onDismissAboutDialog)
    }
    // The update card, whichever button started the download (banner, Settings, About).
    com.winhub.hernandez.ui.UpdateHost()
}

@Composable
private fun AllFilesAccessDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("All Files Access Required") },
        text = {
            Text(
                "In order to grant access to additional storage devices such as USB storage, " +
                "the All Files Access permission must be granted. Press OK to open Android Settings."
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun UpdateBanner(versionName: String, onUpdate: () -> Unit, onDismiss: () -> Unit) {
    val ink = androidx.compose.ui.graphics.Color(0xFF1A1A2E)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Color(0xFFFFC107))
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Update available — V $versionName",
            color = ink,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onUpdate) {
            Text("Update", color = ink, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onDismiss) {
            Text("Skip", color = ink)
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    var update by remember { mutableStateOf<UpdateManager.UpdateInfo?>(null) }
    LaunchedEffect(Unit) {
        UpdateManager.check(context) { info -> activity?.runOnUiThread { update = info } }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outline),
            modifier = androidx.compose.ui.Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Logo + name
                Image(
                    painter = painterResource(R.drawable.splash_logo),
                    contentDescription = null,
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
                Text(
                    text = "WinHub",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                )
                val newer = update?.takeIf { it.isNewer }
                Text(
                    // Read from BuildConfig so it tracks the gradle versionName automatically
                    // and never drifts from the real app version again.
                    text = "V ${BuildConfig.VERSION_NAME}" +
                        (newer?.let { " · latest V ${it.versionName}" } ?: ""),
                    fontSize = 13.sp,
                    color = com.winhub.hernandez.ui.theme.OnSurfaceVariant
                )
                if (newer != null) {
                    Button(
                        onClick = { activity?.let { UpdateManager.downloadAndInstall(it, newer) {} } },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.ui.graphics.Color(0xFF4CAF50)
                        ),
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                    ) { Text("Update now", color = androidx.compose.ui.graphics.Color.White) }
                }

                Spacer(androidx.compose.ui.Modifier.height(4.dp))
                Divider(color = com.winhub.hernandez.ui.theme.Divider)
                Spacer(androidx.compose.ui.Modifier.height(4.dp))

                // Powered by
                AboutSection(title = "Powered By") {
                    AboutRow("Wine",    "Windows compatibility layer")
                    AboutRow("Box64",   "x86_64 emulation on ARM")
                    AboutRow("FEX-Emu", "Fast x86 emulator")
                    AboutRow("Turnip",  "Open-source Vulkan driver")
                }

                Spacer(androidx.compose.ui.Modifier.height(4.dp))
                Divider(color = com.winhub.hernandez.ui.theme.Divider)
                Spacer(androidx.compose.ui.Modifier.height(4.dp))

                // Credits
                AboutSection(title = "Credits") {
                    AboutRow("brunodev85",      "Winlator — original project")
                    AboutRow("MishaMixXx",      "Winlator Bionic")
                    AboutRow("The412Banner",    "WinHub")
                    AboutRow("ptitSeb",         "Box64")
                    AboutRow("WineHQ",          "Wine project")
                    AboutRow("Mesa / Freedreno","Turnip Vulkan driver")
                }

                Spacer(androidx.compose.ui.Modifier.height(8.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                ) { Text("Close") }
            }
        }
    }
}

@Composable
private fun AboutSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = androidx.compose.ui.Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            modifier = androidx.compose.ui.Modifier.padding(bottom = 2.dp)
        )
        content()
    }
}

@Composable
private fun AboutRow(name: String, description: String) {
    Row(
        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
        Spacer(androidx.compose.ui.Modifier.width(8.dp))
        Text(text = description, fontSize = 12.sp, color = com.winhub.hernandez.ui.theme.OnSurfaceVariant)
    }
}
