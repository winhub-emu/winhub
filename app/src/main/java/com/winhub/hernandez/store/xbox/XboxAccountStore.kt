package com.winhub.hernandez.store.xbox

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The signed-in Xbox account: the credential slots the backend stores through the credential channel
 * (XboxCredentials) plus the account's display name, as one JSON record sealed with an Android Keystore
 * AES-256-GCM key. There is no plaintext fallback: if the Keystore is unavailable, nothing is written.
 *
 * Shared by the main process and the sign-in process (":xboxlogin"); XboxCredentials.storage() holds a
 * file lock around every read-modify-write.
 */
internal object XboxAccountStore {
    private const val KEY_ALIAS = "bannerlator_xbox_account_v1"
    private const val RECORD = "account.bin"

    @Volatile var unavailable = false
        private set

    fun dir(app: Context): File = File(app.filesDir, "xbox/account").apply { mkdirs() }

    fun read(app: Context): JSONObject? {
        val file = File(dir(app), RECORD)
        if (!file.isFile) return null
        return try {
            val bytes = file.readBytes()
            require(bytes.size in 13..8_000_000)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
            JSONObject(String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)).also { unavailable = false; scrub(it) }
        } catch (e: Exception) {
            unavailable = true
            XboxDiag.log(app, "account record unreadable: ${e.javaClass.simpleName}")
            null
        }
    }

    /** Keeps the account's sign-in name and gamertag out of every log the shared scrubber handles, as Steam's are. */
    private fun scrub(record: JSONObject) {
        com.winhub.hernandez.store.SteamLogRedactor.registerSecret(record.optString("username"))
        com.winhub.hernandez.store.SteamLogRedactor.registerSecret(record.optString("gamertag"))
    }

    fun write(app: Context, record: JSONObject) {
        scrub(record)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(record.toString().toByteArray(Charsets.UTF_8))
        check(cipher.iv.size == 12)
        val file = File(dir(app), RECORD)
        val staged = File(dir(app), "$RECORD.tmp")
        staged.writeBytes(sealed)
        check(staged.renameTo(file)) { "Xbox account could not be saved" }
        unavailable = false
    }

    fun clear(app: Context) {
        File(dir(app), RECORD).delete()
        File(dir(app), "$RECORD.tmp").delete()
    }

    /**
     * What the store shows for the signed-in account: its Xbox gamertag, else the sign-in email with the
     * part before the @ masked. Never the full email. Null when signed out.
     */
    fun displayName(app: Context): String? {
        val record = read(app) ?: return null
        record.optString("gamertag").takeIf { it.isNotBlank() }?.let { return it }
        return mask(record.optString("username")).takeIf { it.isNotBlank() }
    }

    fun mask(name: String): String {
        val at = name.indexOf('@')
        return if (at <= 0) name.take(1) + "•••" else name.take(1) + "•••" + name.substring(at)
    }

    /** Reads the account's gamertag from Xbox (`xodus-cli profile`) and keeps it in the record; false when it couldn't. */
    fun refreshGamertag(app: Context, credentialGeneration: String? = null): Boolean = try {
        val gamertag = Xodus.run(app, listOf("profile", "--json"), timeout = 30, credentialGeneration = credentialGeneration)
            .optString("gamertag").trim()
        require(gamertag.isNotEmpty() && gamertag.length <= 64 && gamertag.none { it.isISOControl() })
        XboxCredentials.lifecycle(app, credentialGeneration) {
            val record = read(app) ?: error("Xbox account missing")
            record.put("gamertag", gamertag)
            write(app, record)
        }
        true
    } catch (e: Exception) {
        XboxDiag.log(app, "gamertag unavailable: ${e.javaClass.simpleName}")
        false
    }

    /** The signed-in account's name (its sign-in email; used as a cache key, not for display), or null when signed out. */
    fun signedInAs(app: Context): String? =
        read(app)?.optString("username")?.takeIf { it.isNotBlank() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }
}
