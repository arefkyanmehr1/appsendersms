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
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREF_CIPHERTEXT = "api_key_ciphertext"
        private const val PREF_IV = "api_key_iv"
        private const val GCM_TAG_LENGTH = 128
    }

    @Volatile
    private var memoryKey: String? = null

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    @Synchronized
    fun saveApiKey(apiKey: String) {
        val value = apiKey.trim()
        require(value.isNotEmpty()) { "API key must not be blank" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())

        val ciphertext = Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)

        check(
            prefs.edit()
                .putString(PREF_CIPHERTEXT, ciphertext)
                .putString(PREF_IV, iv)
                .commit()
        ) { "Could not persist API key" }

        memoryKey = value
    }

    @Synchronized
    fun getApiKey(): String? {
        memoryKey?.takeIf { it.isNotBlank() }?.let { return it }

        val ciphertext = prefs.getString(PREF_CIPHERTEXT, null) ?: return null
        val ivText = prefs.getString(PREF_IV, null) ?: return null

        return try {
            val ks = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            val secret = (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
                ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secret,
                GCMParameterSpec(GCM_TAG_LENGTH, Base64.decode(ivText, Base64.NO_WRAP))
            )
            String(
                cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)),
                Charsets.UTF_8
            ).also { if (it.isNotBlank()) memoryKey = it }
        } catch (_: Exception) {
            null
        }
    }

    fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()

    @Synchronized
    fun clearApiKey() {
        memoryKey = null
        prefs.edit().remove(PREF_CIPHERTEXT).remove(PREF_IV).commit()
        try {
            KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
                .takeIf { it.containsAlias(KEY_ALIAS) }
                ?.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {
        }
    }

    fun getMaskedApiKey(): String {
        val value = getApiKey() ?: return "---"
        if (value.length <= 8) return "********"
        return value.take(6) + "************" + value.takeLast(4)
    }
}
