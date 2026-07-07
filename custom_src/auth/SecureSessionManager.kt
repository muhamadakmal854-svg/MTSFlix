package com.mts.mtsflix.auth

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * MTSFlix Secure Session Manager
 * Encrypts and decrypts sign-in tokens using a Keystore-backed AES key
 */
object SecureSessionManager {
    private const val KEY_ALIAS = "MTSFlixSecureSessionKey"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val PREFS_NAME = "MTSFlixSecurePrefs"
    private const val KEY_USER_EMAIL = "encrypted_email"
    private const val KEY_SESSION_TIMESTAMP = "encrypted_timestamp"

    init {
        initKeyStore()
    }

    private fun initKeyStore() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
                val spec = android.security.keystore.KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            android.util.Log.e("MTSFlix:Secure", "Error initializing Keystore: ${e.message}")
        }
    }

    private fun getSecretKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    }

    fun encrypt(context: Context, data: String): String? {
        return try {
            val secretKey = getSecretKey() ?: return null
            val cipher = Cipher.getInstance(AES_MODE)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            val base64Encrypted = Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
            val base64Iv = Base64.encodeToString(iv, Base64.DEFAULT)
            "$base64Encrypted:$base64Iv"
        } catch (e: Exception) {
            android.util.Log.e("MTSFlix:Secure", "Encryption failed: ${e.message}")
            null
        }
    }

    fun decrypt(context: Context, encryptedDataWithIv: String): String? {
        return try {
            val parts = encryptedDataWithIv.split(":")
            if (parts.size != 2) return null
            val encryptedBytes = Base64.decode(parts[0], Base64.DEFAULT)
            val iv = Base64.decode(parts[1], Base64.DEFAULT)

            val secretKey = getSecretKey() ?: return null
            val cipher = Cipher.getInstance(AES_MODE)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("MTSFlix:Secure", "Decryption failed: ${e.message}")
            null
        }
    }

    fun saveSession(context: Context, email: String) {
        val encryptedEmail = encrypt(context, email) ?: return
        val encryptedTime = encrypt(context, System.currentTimeMillis().toString()) ?: return

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_USER_EMAIL, encryptedEmail)
            .putString(KEY_SESSION_TIMESTAMP, encryptedTime)
            .apply()
    }

    fun isSessionValid(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedEmail = prefs.getString(KEY_USER_EMAIL, null) ?: return false
        val encryptedTime = prefs.getString(KEY_SESSION_TIMESTAMP, null) ?: return false

        val email = decrypt(context, encryptedEmail) ?: return false
        val timeStr = decrypt(context, encryptedTime) ?: return false

        val timestamp = timeStr.toLongOrNull() ?: return false
        // The session is valid for 30 days
        val thirtyDaysMs = 30L * 24L * 60L * 60L * 1000L
        val age = System.currentTimeMillis() - timestamp
        return age in 0..thirtyDaysMs
    }

    fun clearSession(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
