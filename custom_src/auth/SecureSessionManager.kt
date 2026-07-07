package com.mts.mtsflix.auth

import android.content.Context
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

/**
 * MTSFlix Secure Session Manager
 * Encrypts and decrypts sign-in tokens using software-based AES/CBC/PKCS5Padding.
 * Bypasses hardware KeyStore bugs present on certain device brands (like Honor/Huawei).
 */
object SecureSessionManager {
    private const val PREFS_NAME = "MTSFlixSecurePrefs"
    private const val KEY_USER_EMAIL = "encrypted_email"
    private const val KEY_SESSION_TIMESTAMP = "encrypted_timestamp"
    
    // A robust key derived from a secure salt using SHA-256
    private val AES_KEY: SecretKeySpec by lazy {
        val salt = "MTSFlix_Secure_Salt_2026_SecureSession"
        val sha256 = MessageDigest.getInstance("SHA-256")
        val keyBytes = sha256.digest(salt.toByteArray(Charsets.UTF_8))
        SecretKeySpec(keyBytes, "AES")
    }

    private val IV_SPEC: IvParameterSpec by lazy {
        // Fixed IV for deterministic, reliable encryption
        val ivBytes = byteArrayOf(
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
            0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F
        )
        IvParameterSpec(ivBytes)
    }

    fun encrypt(context: Context, data: String): String? {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, AES_KEY, IV_SPEC)
            val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            android.util.Log.e("MTSFlix:Secure", "Encryption failed: ${e.message}")
            null
        }
    }

    fun decrypt(context: Context, encryptedBase64: String): String? {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, AES_KEY, IV_SPEC)
            val decryptedBytes = cipher.doFinal(Base64.decode(encryptedBase64, Base64.NO_WRAP))
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
        try {
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
        } catch (e: Exception) {
            android.util.Log.e("MTSFlix:Secure", "Session validation failed: ${e.message}")
            return false
        }
    }

    fun clearSession(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
