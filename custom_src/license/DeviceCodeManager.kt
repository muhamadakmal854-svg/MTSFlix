package com.mts.mtsflix.license

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MTSFlix Device Code Manager
 * Generates a unique device code based on hardware fingerprint.
 * Format: MTSF-XXXX-XXXX-XXXX
 * 1 code = 1 device only (tied to ANDROID_ID + hardware info)
 */
object DeviceCodeManager {

    private const val PREF_NAME = "mtsflix_license"
    private const val KEY_DEVICE_CODE = "device_code"
    private const val KEY_VERIFIED = "is_verified"
    private const val KEY_VERIFIED_AT = "verified_at"
    private const val KEY_USERNAME = "username"
    private const val KEY_EXPIRY = "expiry_date"
    private const val KEY_LAST_CHECK = "last_check_ts"

    // Re-verify every 24 hours (in case license was revoked)
    private const val RECHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

    // ─── Device Code Generation ───────────────────────────────────────────────

    /**
     * Get the unique device code. Generated once and cached in SharedPreferences.
     * Based on: ANDROID_ID + Manufacturer + Model + Board
     * Result: reproducible on same device, different on every other device.
     */
    fun getDeviceCode(context: Context): String {
        val prefs = prefs(context)
        val cached = prefs.getString(KEY_DEVICE_CODE, null)
        if (!cached.isNullOrBlank()) return cached

        val code = generateCode(context)
        prefs.edit().putString(KEY_DEVICE_CODE, code).apply()
        return code
    }

    private fun generateCode(context: Context): String {
        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "NOID"
        } catch (e: Exception) { "NOID" }

        // Combine device identifiers for uniqueness
        val raw = listOf(
            androidId,
            Build.MANUFACTURER,
            Build.MODEL,
            Build.BOARD,
            Build.HARDWARE
        ).joinToString("|").uppercase(Locale.US)

        // SHA-256 → take first 12 chars → format as MTSF-XXXX-XXXX-XXXX
        val hash = sha256hex(raw).uppercase(Locale.US)
        val part = hash.take(12)
        return "MTSF-${part.substring(0, 4)}-${part.substring(4, 8)}-${part.substring(8, 12)}"
    }

    private fun sha256hex(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // Fallback: simple hash if SHA-256 unavailable (shouldn't happen on Android)
            input.hashCode().toString(16).padStart(12, '0').take(12)
        }
    }

    // ─── Verification State ───────────────────────────────────────────────────

    /**
     * Check if this device has a valid, non-expired license cached locally.
     * Returns false if:
     * - Never verified
     * - License expired
     * - Last check was > 24h ago (force re-verify)
     */
    fun isVerifiedLocally(context: Context): Boolean {
        val prefs = prefs(context)
        if (!prefs.getBoolean(KEY_VERIFIED, false)) return false

        val expiryStr = prefs.getString(KEY_EXPIRY, null) ?: return false
        if (isExpired(expiryStr)) return false

        // Force re-check after 24 hours
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)
        val now = System.currentTimeMillis()
        if (now - lastCheck > RECHECK_INTERVAL_MS) return false

        return true
    }

    fun setVerified(context: Context, username: String, expiryDate: String) {
        prefs(context).edit()
            .putBoolean(KEY_VERIFIED, true)
            .putString(KEY_USERNAME, username)
            .putString(KEY_EXPIRY, expiryDate)
            .putLong(KEY_VERIFIED_AT, System.currentTimeMillis())
            .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
            .apply()
    }

    fun clearVerification(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_VERIFIED, false)
            .remove(KEY_USERNAME)
            .remove(KEY_EXPIRY)
            .remove(KEY_LAST_CHECK)
            .apply()
    }

    fun getUsername(context: Context): String? =
        prefs(context).getString(KEY_USERNAME, null)

    fun getExpiryDate(context: Context): String? =
        prefs(context).getString(KEY_EXPIRY, null)

    fun isExpiredLocally(context: Context): Boolean {
        val expiry = getExpiryDate(context) ?: return true
        return isExpired(expiry)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun isExpired(dateStr: String): Boolean {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val expiry = sdf.parse(dateStr) ?: return true
            expiry.before(Date())
        } catch (e: Exception) { true }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
