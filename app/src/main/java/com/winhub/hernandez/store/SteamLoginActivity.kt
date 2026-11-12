package com.winhub.hernandez.store

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.winhub.hernandez.ui.components.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.winhub.hernandez.ui.screens.DeckButton
import com.winhub.hernandez.ui.screens.DeckConfigDialog
import com.winhub.hernandez.ui.screens.DeckConfigTextField
import com.winhub.hernandez.ui.screens.DeckGroup
import com.winhub.hernandez.ui.screens.DeckPalette
import com.winhub.hernandez.ui.theme.WinlatorTheme

class SteamLoginActivity : ComponentActivity(), SteamAuthManager.AuthListener {

    companion object {
        /** Set when sign-in was opened from Deck Mode; success returns to the Deck store, not the classic shell. */
        const val EXTRA_FROM_DECK = "from_deck"
    }

    private var username by mutableStateOf("")
    private var password by mutableStateOf("")
    private var isLoading by mutableStateOf(false)
    private var statusText by mutableStateOf("")
    private var isStatusError by mutableStateOf(false)
    private var guardDialog by mutableStateOf<GuardDialogData?>(null)

    private var connectWaitListener: SteamRepository.SteamEventListener? = null
    private var pendingUsername: String? = null
    private var pendingPassword: String? = null
    private var reachState = 0
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val connectTimeoutRunnable = Runnable {
        connectWaitListener?.let { SteamRepository.getInstance().removeListener(it) }
        connectWaitListener = null
        pendingUsername = null; pendingPassword = null
        val msg = when (reachState) {
            1    -> "Steam CM connection timed out. Port 27017 may be blocked — try a VPN or mobile data."
            2    -> "Steam is blocked on your network. A VPN is required."
            3    -> "No internet connection detected."
            else -> "Could not reach Steam servers. Check your network."
        }
        onFailure(msg)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Honour the user's App-orientation preference (Appearance -> AUTO / PORTRAIT / LANDSCAPE).
        // The whole Steam section previously ignored it: these activities pinned themselves in the
        // manifest and never asked. Applied before any content so the first frame is already in the
        // requested orientation. The game's XServerDisplayActivity is deliberately NOT touched.
        com.winhub.hernandez.core.AppOrientation.apply(this)
        // The sign-in flow waits on the Steam CM "Connected" event, and that connection is owned by
        // SteamForegroundService. SteamMainActivity used to be the only entry point that started it;
        // opening sign-in directly (Deck Stores / the preflight "Sign in") left the repository
        // offline, so every login timed out with "Could not reach Steam servers". Start it here so
        // the screen works no matter who launches it.
        SteamForegroundService.start(this)
        setContent {
            StoreKeyboardTheme {
                DeckStoreTheme {
                SteamLoginScreen(
                    username = username,
                    password = password,
                    isLoading = isLoading,
                    statusText = statusText,
                    isStatusError = isStatusError,
                    onUsernameChange = { username = it },
                    onPasswordChange = { password = it },
                    onLoginClick = { onLoginClicked() },
                    onQrClick = {
                        startActivity(Intent(this@SteamLoginActivity, QrLoginActivity::class.java))
                    },
                )
                guardDialog?.let { data ->
                    SteamGuardDialog(
                        title = data.title,
                        message = data.message,
                        onDismiss = { guardDialog = null },
                        onSubmit = { code ->
                            guardDialog = null
                            statusText = "Verifying…"
                            isStatusError = false
                            isLoading = true
                            SteamAuthManager.getInstance().submitGuardCode(code)
                        },
                        onCancel = {
                            guardDialog = null
                            SteamAuthManager.getInstance().cancelAuth()
                            statusText = "Sign-in cancelled."
                            isStatusError = false
                            isLoading = false
                        },
                    )
                }
            
                }}
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(connectTimeoutRunnable)
        connectWaitListener?.let { SteamRepository.getInstance().removeListener(it) }
        connectWaitListener = null
        super.onDestroy()
        SteamAuthManager.getInstance().cancelAuth()
        // SteamMainActivity starts the foreground service BEFORE the sign-in check (the sign-in
        // flow needs the CM channel — it waits on the "Connected" event), so leaving this screen
        // without a session used to strand an ongoing Steam notification with nothing in the app
        // able to stop it. Tear it down on the way out unless a download owns the session.
        val signedIn = try { SteamPrefs.isLoggedIn } catch (t: Throwable) { false }
        if (isFinishing && !isChangingConfigurations && !signedIn) {
            SteamForegroundService.stopIfIdle(applicationContext)
        }
    }

    private fun onLoginClicked() {
        if (isLoading) return
        val u = username.trim()
        val p = password
        if (u.isEmpty()) { statusText = "Enter your username."; isStatusError = true; return }
        if (p.isEmpty())  { statusText = "Enter your password."; isStatusError = true; return }
        hideKeyboard()
        isStatusError = false
        statusText = "Connecting to Steam\u2026"
        isLoading = true

        val repo = SteamRepository.getInstance()
        if (repo.isConnected) {
            SteamAuthManager.getInstance().startCredentialLogin(u, p, this)
        } else {
            pendingUsername = u
            pendingPassword = p
            val listener = object : SteamRepository.SteamEventListener {
                override fun onEvent(event: String) {
                    when {
                        event == "Reachable"     -> reachState = 1
                        event == "SteamBlocked"  -> reachState = 2
                        event == "NoInternet"    -> reachState = 3
                        event == "Connected" -> {
                            repo.removeListener(this)
                            connectWaitListener = null
                            mainHandler.removeCallbacks(connectTimeoutRunnable)
                            val u = pendingUsername ?: return
                            val p = pendingPassword ?: return
                            pendingUsername = null; pendingPassword = null
                            runOnUiThread { SteamAuthManager.getInstance().startCredentialLogin(u, p, this@SteamLoginActivity) }
                        }
                        event.startsWith("Disconnected") -> {
                            repo.removeListener(this)
                            connectWaitListener = null
                            pendingUsername = null; pendingPassword = null
                            runOnUiThread { onFailure("Could not connect to Steam") }
                        }
                    }
                }
            }
            reachState = 0
            connectWaitListener = listener
            repo.addListener(listener)
            mainHandler.postDelayed(connectTimeoutRunnable, 10_000L)
        }
    }

    override fun onSteamGuardEmailRequired(emailDomain: String, codeWrong: Boolean) {
        isLoading = false
        statusText = ""
        guardDialog = GuardDialogData(
            title   = if (codeWrong) "Incorrect code — try again" else "Steam Guard",
            message = "Enter the code Steam sent to your email ending in \u2026$emailDomain",
        )
    }

    override fun onSteamGuardTotpRequired(codeWrong: Boolean) {
        isLoading = false
        statusText = ""
        guardDialog = GuardDialogData(
            title   = if (codeWrong) "Incorrect code — try again" else "Steam Guard",
            message = "Enter the code from your Steam Guard Mobile Authenticator app",
        )
    }

    override fun onDeviceConfirmationRequired() {
        statusText = "Approve the login in your Steam mobile app\u2026"
        isStatusError = false
        isLoading = true
    }

    override fun onSuccess(username: String, refreshToken: String) {
        SteamRepository.getInstance().loginWithToken(username, refreshToken)
        statusText = "Signed in!"
        isStatusError = false
        isLoading = false
        // Deck Mode opened this screen from its own Steam store pane. Finish straight back into
        // that pane (which re-reads the session on resume) instead of launching the classic
        // store-first shell, or the user lands in the old library after their first login.
        if (intent.getBooleanExtra(EXTRA_FROM_DECK, false)) {
            finish()
            return
        }
        // Back to the store-first host (singleTop + CLEAR_TOP reuses the instance that launched
        // us, so its onNewIntent/onResume picks up the fresh login without a second copy).
        startActivity(
            Intent(this, SteamMainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }

    override fun onFailure(reason: String) {
        isLoading = false
        statusText = "Sign-in failed: $reason"
        isStatusError = true
    }

    private fun hideKeyboard() {
        currentFocus?.let { v ->
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(v.windowToken, 0)
        }
    }
}

private data class GuardDialogData(
    val title: String,
    val message: String,
)

@Composable
private fun SteamGuardDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    DeckConfigDialog(title = title, onDismiss = onDismiss, subtitle = message) {
        DeckConfigTextField(
            label = "Code",
            value = code,
            onValueChange = { code = it.take(5) },
            numeric = title.contains("Authenticator"),
        )
        Spacer(Modifier.height(12.dp))
        DeckButton(
            text = "Submit",
            primary = true,
            onClick = { onSubmit(code.trim()) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        DeckButton(
            text = "Cancel",
            primary = false,
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SteamLoginScreen(
    username: String,
    password: String,
    isLoading: Boolean,
    statusText: String,
    isStatusError: Boolean,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    onQrClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeckPalette.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Steam",
            color = DeckPalette.onBackground,
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Sign in to your account",
            color = DeckPalette.onSurfaceVariant,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(24.dp))

        Column(modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
            DeckGroup("Account") {
                DeckConfigTextField(
                    label = "Username",
                    value = username,
                    onValueChange = onUsernameChange,
                    imeAction = ImeAction.Next,
                )
                DeckConfigTextField(
                    label = "Password",
                    value = password,
                    onValueChange = onPasswordChange,
                    password = true,
                    imeAction = ImeAction.Done,
                    onImeAction = onLoginClick,
                )
            }
            Spacer(Modifier.height(16.dp))

            DeckButton(
                text = if (isLoading) "Signing in\u2026" else "Sign In",
                primary = true,
                onClick = onLoginClick,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            // QR sign-in re-enabled: a QR-originated session stores the same
            // username + refresh_token as a password login (SteamQrAuthManager →
            // saveSession), so it is recovered by the same logoff/reconnect path
            // (SteamRepository.onLoggedOff / reconnectNow). The QR screen shows an
            // advisory to fall back to username + password if downloads or the
            // session keep dropping after signing in this way.
            DeckButton(
                text = "Sign in with QR Code",
                primary = false,
                onClick = onQrClick,
                modifier = Modifier.fillMaxWidth(),
            )

            if (isLoading) {
                Spacer(Modifier.height(14.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp).align(Alignment.CenterHorizontally),
                    color = DeckPalette.primary,
                    strokeWidth = 2.dp,
                )
            }

            if (statusText.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = statusText,
                    color = if (isStatusError) DeckPalette.error else DeckPalette.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
