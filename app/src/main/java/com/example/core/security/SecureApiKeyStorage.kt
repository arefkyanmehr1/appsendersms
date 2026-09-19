package com.example.core.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the API credential only as AES-256-GCM ciphertext protected by Android Keystore.
 *
 * There is deliberately no plaintext/Base64 fallback: Base64 is encoding, not encryption.
 * If Keystore is unavailable/corrupted, the credential is treated as unavailable and the
 * user must authenticate again.
 */
class SecureApiKeyStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "paylink_secure_prefs"
        private const val KEY_ALIAS = "PayLinkApiKeyAlias"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREF_ENCRYPTED_API_KEY = "enc_api_key"
        private const val PREF_IV = "enc_iv"
        private const val GCM_TAG_LENGTH = 128
    }

    @Volatile
    private var inMemoryApiKey: String? = null

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }

    @Synchronized
    fun saveApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        require(trimmed.isNotBlank()) { "API key must not be blank" }

        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val encrypted = Base64.encodeToString(
            cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP
        )
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)

        check(
            prefs.edit()
                .putString(PREF_ENCRYPTED_API_KEY, encrypted)
                .putString(PREF_IV, iv)
                .commit()
        ) { "Unable to persist API key" }

        inMemoryApiKey = trimmed
    }

    @Synchronized
    fun getApiKey(): String? {
        inMemoryApiKey?.takeIf { it.isNotBlank() }?.let { return it }

        val encrypted = prefs.getString(PREF_ENCRYPTED_API_KEY, null) ?: return null
        val ivString = prefs.getString(PREF_IV, null) ?: return null

        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            val secretKey =
                (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
                    ?: return null

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey,
                GCMParameterSpec(GCM_TAG_LENGTH, Base64.decode(ivString, Base64.NO_WRAP))
            )

            String(
                cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)),
                Charsets.UTF_8
            ).also { decrypted ->
                if (decrypted.isNotBlank()) inMemoryApiKey = decrypted
            }
        } catch (_: Exception) {
            // Never fall back to plaintext/encoded credentials.
            null
        }
    }

    fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()

    @Synchronized
    fun clearApiKey() {
        inMemoryApiKey = null
        prefs.edit()
            .remove(PREF_ENCRYPTED_API_KEY)
            .remove(PREF_IV)
            .commit()

        try {
            KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
                .takeIf { it.containsAlias(KEY_ALIAS) }
                ?.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {
        }
    }

    fun getMaskedApiKey(): String {
        val key = getApiKey() ?: return "---"
        if (key.length <= 8) return "********"
        return key.take(6) + "************" + key.takeLast(4)
    }
}
