package com.mts.mtsflix.license

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection

/**
 * MTSFlix License Verifier
 * Checks device license against GitHub licenses.json
 *
 * License source: https://raw.githubusercontent.com/muhamadakmal854-svg/MTSFlix/main/licenses.json
 *
 * JSON structure:
 * {
 *   "version": 1,
 *   "licenses": [
 *     {
 *       "deviceCode": "MTSF-XXXX-XXXX-XXXX",
 *       "username": "Ahmad Ali",
 *       "email": "ahmad@example.com",
 *       "expiredAt": "2026-12-31",
 *       "active": true,
 *       "addedAt": "2026-07-05",
 *       "note": ""
 *     }
 *   ]
 * }
 */
object LicenseVerifier {

    private const val TAG = "MTSFlix:License"

    // URL to licenses.json in your GitHub repo (main branch, publicly readable)
    const val LICENSES_URL =
        "https://raw.githubusercontent.com/muhamadakmal854-svg/MTSFlix/main/licenses.json"

    // ─── Result Data Class ────────────────────────────────────────────────────

    data class LicenseResult(
        val status: Status,
        val username: String = "",
        val expiryDate: String = "",
        val message: String = "",
        val deviceCode: String = ""
    ) {
        val isValid: Boolean get() = status == Status.VALID
    }

    enum class Status {
        VALID,           // License found, active, not expired
        EXPIRED,         // License found but expired
        INACTIVE,        // License found but deactivated by admin
        NOT_FOUND,       // Device code not in license list
        NETWORK_ERROR,   // Cannot reach GitHub (offline?)
        PARSE_ERROR      // licenses.json malformed
    }

    // ─── Main Verification ────────────────────────────────────────────────────

    /**
     * Verify device code against GitHub licenses.json
     * Must be called from a coroutine (uses Dispatchers.IO)
     */
    suspend fun verify(deviceCode: String): LicenseResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Verifying: $deviceCode")

        val jsonText = try {
            fetchLicenses()
        } catch (e: Exception) {
            Log.e(TAG, "Network error: ${e.message}")
            return@withContext LicenseResult(
                status = Status.NETWORK_ERROR,
                deviceCode = deviceCode,
                message = "Tiada sambungan internet. Periksa WiFi/Data anda dan cuba semula."
            )
        }

        val licenses = try {
            parseLicenses(jsonText)
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
            return@withContext LicenseResult(
                status = Status.PARSE_ERROR,
                deviceCode = deviceCode,
                message = "Ralat pembacaan data lesen. Cuba sebentar lagi."
            )
        }

        // Search for this device code
        for (entry in licenses) {
            if (entry.deviceCode.trim().equals(deviceCode.trim(), ignoreCase = true)) {
                Log.i(TAG, "Found license for: ${entry.username}")

                if (!entry.active) {
                    return@withContext LicenseResult(
                        status = Status.INACTIVE,
                        username = entry.username,
                        deviceCode = deviceCode,
                        message = "Lesen peranti tidak aktif. Sila hubungi admin MTS."
                    )
                }

                if (isExpired(entry.expiredAt)) {
                    return@withContext LicenseResult(
                        status = Status.EXPIRED,
                        username = entry.username,
                        expiryDate = entry.expiredAt,
                        deviceCode = deviceCode,
                        message = "Lesen tamat tempoh pada ${entry.expiredAt}. Sila hubungi admin MTS untuk perbaharui."
                    )
                }

                return@withContext LicenseResult(
                    status = Status.VALID,
                    username = entry.username,
                    expiryDate = entry.expiredAt,
                    deviceCode = deviceCode,
                    message = "Selamat datang, ${entry.username}! Lesen sah sehingga ${entry.expiredAt}."
                )
            }
        }

        // Not found
        Log.w(TAG, "Device code not found: $deviceCode")
        LicenseResult(
            status = Status.NOT_FOUND,
            deviceCode = deviceCode,
            message = "Peranti belum didaftarkan. Hantar kod di atas kepada admin MTS untuk akses."
        )
    }

    // ─── HTTP Fetch ───────────────────────────────────────────────────────────

    private fun fetchLicenses(): String {
        val url = URL(LICENSES_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("User-Agent", "MTSFlix/1.0")
        }
        return try {
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().readText()
            } else {
                throw Exception("HTTP ${conn.responseCode}")
            }
        } finally {
            conn.disconnect()
        }
    }

    // ─── JSON Parser ─────────────────────────────────────────────────────────

    data class LicenseEntry(
        val deviceCode: String,
        val username: String,
        val email: String,
        val expiredAt: String,
        val active: Boolean,
        val addedAt: String,
        val note: String
    )

    private fun parseLicenses(json: String): List<LicenseEntry> {
        val root = JSONObject(json)
        val arr = root.getJSONArray("licenses")
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            LicenseEntry(
                deviceCode = obj.optString("deviceCode", ""),
                username = obj.optString("username", "User"),
                email = obj.optString("email", ""),
                expiredAt = obj.optString("expiredAt", "2099-12-31"),
                active = obj.optBoolean("active", true),
                addedAt = obj.optString("addedAt", ""),
                note = obj.optString("note", "")
            )
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun isExpired(dateStr: String): Boolean {
        if (dateStr.isBlank()) return false
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val expiry = sdf.parse(dateStr) ?: return false
            expiry.before(java.util.Date())
        } catch (e: Exception) { false }
    }
}
