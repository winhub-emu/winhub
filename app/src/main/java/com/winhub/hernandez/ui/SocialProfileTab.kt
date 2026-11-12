package com.winhub.hernandez.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.store.SocialHubStore
import com.winhub.hernandez.store.SocialProfiles
import com.winhub.hernandez.ui.screens.ControllerTextField
import com.winhub.hernandez.ui.screens.DeckGroup
import com.winhub.hernandez.ui.screens.DeckRow
import com.winhub.hernandez.ui.screens.LocalDeckMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val AVATAR_PX = 256

/**
 * Social Hub "Profile" tab: pick a display name and picture that everyone in the Hub sees instead of
 * the GitHub ones (GitHub's can't be changed from here). Stored via [SocialProfiles].
 */
@Composable
internal fun SocialProfileContent(onGoFeed: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val account by SocialHubStore.account.collectAsState()
    val profiles by SocialProfiles.profiles.collectAsState()
    val cs = MaterialTheme.colorScheme
    val acct = account

    val deck = LocalDeckMode.current

    Column(
        horizontalAlignment = if (deck) Alignment.Start else Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).then(if (deck) Modifier else Modifier.padding(16.dp)),
    ) {
        if (acct == null) {
            if (deck) {
                SocialEmpty(
                    "Sign in to edit your profile",
                    "Your Social Hub profile is tied to your GitHub sign-in. Sign in from the Feed tab first.",
                    modifier = Modifier.heightIn(min = 240.dp),
                ) { SocialButton("Go to Feed to sign in", onClick = onGoFeed) }
            } else {
                Text("Sign in to edit your profile", color = cs.onSurface, fontSize = 18.sp)
                Text(
                    "Your Social Hub profile is tied to your GitHub sign-in. Sign in from the Feed tab first.",
                    color = cs.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center,
                )
                Button(onClick = onGoFeed, modifier = Modifier.heightIn(min = 48.dp)) { Text("Go to Feed to sign in") }
            }
            return@Column
        }

        val mine = profiles[acct.login.lowercase()]
        var name by remember(acct.login, mine?.name) { mutableStateOf(mine?.name.orEmpty()) }
        var picked by remember { mutableStateOf<Bitmap?>(null) }
        var pickedBytes by remember { mutableStateOf<ByteArray?>(null) }
        var working by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf<String?>(null) }

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { squareJpeg(context, uri) }
                if (bytes == null) {
                    message = "Couldn't read that picture."
                } else {
                    pickedBytes = bytes
                    picked = withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                    message = null
                }
            }
        }

        val save = {
            scope.launch {
                working = true
                message = null
                var avatarUrl = mine?.avatar
                val bytes = pickedBytes
                if (bytes != null) {
                    avatarUrl = SocialHubStore.uploadImage(bytes, "avatar.jpg")
                    if (avatarUrl == null) {
                        message = "Picture upload failed. Try again."
                        working = false
                        return@launch
                    }
                }
                val err = SocialProfiles.save(name, avatarUrl)
                message = err ?: "Profile saved. Everyone will see it."
                if (err == null) { picked = null; pickedBytes = null }
                working = false
            }
            Unit
        }
        val reset = {
            scope.launch {
                working = true
                val err = SocialProfiles.save("", null)
                message = err ?: "Back to your GitHub name and picture."
                if (err == null) { picked = null; pickedBytes = null; name = "" }
                working = false
            }
            Unit
        }
        val canReset = !working && (mine != null || picked != null)

        val shownBitmap = picked
        if (deck) {
            DeckGroup("Profile") {
                DeckRow(label = "Picture", hint = "Choose a new profile picture") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (shownBitmap != null) {
                            Image(
                                bitmap = shownBitmap.asImageBitmap(),
                                contentDescription = "New profile picture",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(44.dp).clip(CircleShape).border(2.dp, cs.primary, CircleShape),
                            )
                        } else {
                            AccountAvatar(
                                avatarUrl = mine?.avatar ?: acct.avatarUrl,
                                size = 44.dp,
                                modifier = Modifier.border(2.dp, cs.primary, CircleShape),
                            )
                        }
                        SocialButton("Change", onClick = { picker.launch("image/*") }, primary = false)
                    }
                }
                DeckRow(
                    label = "Display name",
                    hint = "${name.length}/${SocialProfiles.MAX_NAME} - leave empty to use @${acct.login}",
                ) {
                    ControllerTextField(
                        value = name,
                        onValueChange = { name = SocialProfiles.sanitizeName(it) },
                        hint = acct.login,
                        singleLine = true,
                        modifier = Modifier.width(220.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SocialButton(if (working) "Saving…" else "Save profile", onClick = { save() }, enabled = !working)
                SocialButton("Reset to GitHub", onClick = { reset() }, primary = false, enabled = canReset)
            }
            message?.let { Text(it, color = cs.onSurfaceVariant, fontSize = 14.sp) }
            Text(
                "Your name and picture are public and shown on your posts and replies.",
                color = cs.onSurfaceVariant, fontSize = 13.sp,
            )
            return@Column
        }

        if (shownBitmap != null) {
            Image(
                bitmap = shownBitmap.asImageBitmap(),
                contentDescription = "New profile picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(112.dp).clip(CircleShape).border(2.dp, cs.primary, CircleShape).clickable { picker.launch("image/*") },
            )
        } else {
            AccountAvatar(
                avatarUrl = mine?.avatar ?: acct.avatarUrl,
                size = 112.dp,
                modifier = Modifier.border(2.dp, cs.primary, CircleShape).clickable { picker.launch("image/*") },
            )
        }
        OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Change picture") }

        OutlinedTextField(
            value = name,
            onValueChange = { name = SocialProfiles.sanitizeName(it) },
            label = { Text("Display name") },
            placeholder = { Text(acct.login) },
            singleLine = true,
            supportingText = { Text("${name.length}/${SocialProfiles.MAX_NAME} · leave empty to use @${acct.login}") },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            enabled = !working,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            onClick = { save() },
        ) { Text(if (working) "Saving…" else "Save profile") }

        OutlinedButton(
            enabled = canReset,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            onClick = { reset() },
        ) { Text("Reset to GitHub") }

        message?.let { Text(it, color = cs.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center) }
        Text(
            "Your name and picture are public and shown on your posts and replies.",
            color = cs.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center,
        )
    }
}

/** Decodes [uri], centre-crops to a square, scales to [AVATAR_PX] and returns JPEG bytes (null on failure). Blocking. */
private fun squareJpeg(context: android.content.Context, uri: Uri): ByteArray? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= AVATAR_PX * 2 && bounds.outHeight / (sample * 2) >= AVATAR_PX * 2) sample *= 2
    val src = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    }
    if (src == null) null else {
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, AVATAR_PX, AVATAR_PX, true)
        ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
            out.toByteArray()
        }
    }
} catch (e: java.io.IOException) {
    android.util.Log.w("SocialProfileTab", "squareJpeg", e)
    null
} catch (e: SecurityException) {
    android.util.Log.w("SocialProfileTab", "squareJpeg", e)
    null
}
