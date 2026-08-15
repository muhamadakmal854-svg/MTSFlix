package com.mts.mtsflix.license

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * MTSFlix Provider Lock Manager v1.1.4
 * Menguruskan kunci PIN untuk Provider (Extensions), pengesahan sesi, dan penyelarasan akaun Google.
 */
object ProviderLockManager {

    private const val KEY_PIN_HASH = "mtsflix_provider_lock_pin_hash"
    private const val KEY_LOCKED_PROVIDERS = "mtsflix_locked_providers_set"
    private const val DEFAULT_PIN = "0000"

    // Sesi aktif: menyimpan senarai Provider yang telah dimasukkan PIN dengan betul semasa aplikasi dibuka
    private val unlockedSessionProviders = mutableSetOf<String>()

    private fun getPrefs(context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    }

    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hasCustomPin(context: Context): Boolean {
        return getPrefs(context).contains(KEY_PIN_HASH)
    }

    fun setPin(context: Context, newPin: String) {
        val hash = hashPin(newPin)
        getPrefs(context).edit().putString(KEY_PIN_HASH, hash).apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val storedHash = getPrefs(context).getString(KEY_PIN_HASH, null) ?: hashPin(DEFAULT_PIN)
        return hashPin(pin) == storedHash
    }

    fun getLockedProviders(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_LOCKED_PROVIDERS, emptySet()) ?: emptySet()
    }

    fun isProviderLocked(context: Context, providerName: String?): Boolean {
        if (providerName.isNullOrBlank()) return false
        val lockedSet = getLockedProviders(context)
        return lockedSet.contains(providerName) || lockedSet.contains(providerName.lowercase())
    }

    fun setProviderLocked(context: Context, providerName: String, locked: Boolean) {
        val current = getLockedProviders(context).toMutableSet()
        if (locked) {
            current.add(providerName)
        } else {
            current.remove(providerName)
        }
        getPrefs(context).edit().putStringSet(KEY_LOCKED_PROVIDERS, current).apply()
    }

    fun isSessionUnlocked(providerName: String?): Boolean {
        if (providerName.isNullOrBlank()) return true
        return unlockedSessionProviders.contains(providerName) || unlockedSessionProviders.contains(providerName.lowercase())
    }

    fun unlockSession(providerName: String?) {
        if (!providerName.isNullOrBlank()) {
            unlockedSessionProviders.add(providerName)
            unlockedSessionProviders.add(providerName.lowercase())
        }
    }

    /**
     * Kosongkan sesi apabila pengguna keluar aplikasi / masuk semula
     */
    fun clearSessionLocks() {
        unlockedSessionProviders.clear()
    }

    fun requiresPin(context: Context, providerName: String?): Boolean {
        if (providerName.isNullOrBlank()) return false
        val locked = isProviderLocked(context, providerName)
        val sessionUnlocked = isSessionUnlocked(providerName)
        return locked && !sessionUnlocked
    }

    // --- GOOGLE / GIST SYNC BACKUP & RESTORE ---

    fun exportBackupJson(context: Context): JSONObject {
        val json = JSONObject()
        val storedHash = getPrefs(context).getString(KEY_PIN_HASH, "")
        json.put("pin_hash", storedHash)

        val lockedArray = JSONArray()
        getLockedProviders(context).forEach { lockedArray.put(it) }
        json.put("locked_providers", lockedArray)

        return json
    }

    fun importBackupJson(context: Context, json: JSONObject?) {
        if (json == null) return
        val prefs = getPrefs(context).edit()

        if (json.has("pin_hash")) {
            val hash = json.optString("pin_hash", "")
            if (hash.isNotEmpty()) {
                prefs.putString(KEY_PIN_HASH, hash)
            }
        }

        if (json.has("locked_providers")) {
            val arr = json.optJSONArray("locked_providers")
            if (arr != null) {
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) {
                    set.add(arr.getString(i))
                }
                prefs.putStringSet(KEY_LOCKED_PROVIDERS, set)
            }
        }

        prefs.apply()
    }
}
