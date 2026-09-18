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
        private const val PREF_BACKUP_KEY = "backup_api_key"
        private const val GCM_TAG_LENGTH = 128
    }

    @Volatile
    private var inMemoryApiKey: String? = null

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    @Synchronized
    fun saveApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isBlank()) return
        inMemoryApiKey = trimmed

        val backupEnc = Base64.encodeToString(trimmed.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val editor = prefs.edit().putString(PREF_BACKUP_KEY, backupEnc)

        try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))

            val encBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)

            editor.putString(PREF_ENCRYPTED_API_KEY, encBase64)
                .putString(PREF_IV, ivBase64)
                .commit()
        } catch (e: Exception) {
            editor.putString(PREF_ENCRYPTED_API_KEY, backupEnc)
                .putString(PREF_IV, "plain_fallback")
                .commit()
        }
    }

    @Synchronized
    fun getApiKey(): String? {
        if (!inMemoryApiKey.isNullOrBlank()) {
            return inMemoryApiKey
        }

        // Try decrypting with KeyStore
        val encBase64 = prefs.getString(PREF_ENCRYPTED_API_KEY, null)
        val ivBase64 = prefs.getString(PREF_IV, null)

        if (!encBase64.isNullOrBlank() && !ivBase64.isNullOrBlank()) {
            if (ivBase64 == "plain_fallback") {
                val key = String(Base64.decode(encBase64, Base64.NO_WRAP), Charsets.UTF_8)
                inMemoryApiKey = key
                return key
            }

            try {
                val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
                val secretKey = (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
                if (secretKey != null) {
                    val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
                    val encryptedBytes = Base64.decode(encBase64, Base64.NO_WRAP)

                    val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                    val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                    val decryptedBytes = cipher.doFinal(encryptedBytes)
                    val key = String(decryptedBytes, Charsets.UTF_8)
                    inMemoryApiKey = key
                    return key
                }
            } catch (_: Exception) {}
        }

        // Fallback to backup key in prefs
        val backupEnc = prefs.getString(PREF_BACKUP_KEY, null)
        if (!backupEnc.isNullOrBlank()) {
            try {
                val key = String(Base64.decode(backupEnc, Base64.NO_WRAP), Charsets.UTF_8)
                inMemoryApiKey = key
                return key
            } catch (_: Exception) {}
        }

        return null
    }

    fun hasApiKey(): Boolean {
        return !getApiKey().isNullOrBlank()
    }

    @Synchronized
    fun clearApiKey() {
        inMemoryApiKey = null
        prefs.edit()
            .remove(PREF_ENCRYPTED_API_KEY)
            .remove(PREF_IV)
            .remove(PREF_BACKUP_KEY)
            .commit()
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        } catch (_: Exception) {}
    }

    fun getMaskedApiKey(): String {
        val key = getApiKey() ?: return "---"
        if (key.length <= 8) {
            return "********"
        }
        val prefix = key.take(6)
        val suffix = key.takeLast(4)
        val stars = "*".repeat(12)
        return "$prefix$stars$suffix"
    }
}
