package com.mts.mtsflix.license

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

/**
 * MTSFlix Provider Lock Manager v1.1.4
 * Menguruskan kunci PIN untuk Provider (Extensions) tertentu.
 */
object ProviderLockManager {

    private const val PREF_NAME = "mtsflix_provider_lock_prefs"
    private const val KEY_PIN_HASH = "provider_lock_pin_hash"
    private const val KEY_LOCKED_PROVIDERS = "locked_providers_set"
    private const val DEFAULT_PIN = "0000"

    // Sesi aktif: menyimpan senarai Provider yang telah dimasukkan PIN dengan betul semasa aplikasi dibuka
    private val unlockedSessionProviders = mutableSetOf<String>()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
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

    fun requiresPin(context: Context, providerName: String?): Boolean {
        if (providerName.isNullOrBlank()) return false
        val locked = isProviderLocked(context, providerName)
        val sessionUnlocked = isSessionUnlocked(providerName)
        return locked && !sessionUnlocked
    }
}
