package com.mts.mtsflix.license

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/**
 * MTSFlix Profile Manager
 * Menguruskan sehingga 5 profil di bawah 1 akaun Google
 */
data class MtsProfile(
    val id: String,
    val name: String,
    val avatarColor: String,   // hex e.g. "#E50914"
    val isKids: Boolean,
    val pinHash: String?,      // SHA-256 hash of PIN, null = no PIN
    val createdAt: Long
) {
    val avatarLetter: String get() = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    fun checkPin(pin: String): Boolean {
        if (pinHash == null) return true
        return sha256(pin) == pinHash
    }

    companion object {
        fun sha256(input: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }

        val AVATAR_COLORS = listOf(
            "#E50914", "#E87C03", "#F5C518", "#2196F3",
            "#4CAF50", "#9C27B0", "#FF5722", "#00BCD4"
        )

        fun fromJson(obj: JSONObject) = MtsProfile(
            id          = obj.getString("id"),
            name        = obj.getString("name"),
            avatarColor = obj.optString("avatarColor", "#E50914"),
            isKids      = obj.optBoolean("isKids", false),
            pinHash     = obj.optString("pinHash", "").ifBlank { null },
            createdAt   = obj.optLong("createdAt", System.currentTimeMillis())
        )
    }

    fun toJson() = JSONObject().apply {
        put("id",          id)
        put("name",        name)
        put("avatarColor", avatarColor)
        put("isKids",      isKids)
        put("pinHash",     pinHash ?: "")
        put("createdAt",   createdAt)
    }
}

object ProfileManager {

    private const val KEY_PROFILES        = "MTSFLIX_PROFILES_V2"
    private const val KEY_ACTIVE_PROFILE  = "MTSFLIX_ACTIVE_PROFILE_ID"
    const val MAX_PROFILES = 5

    // ── Load / Save ──────────────────────────────────────────────────────────

    fun loadProfiles(context: Context): List<MtsProfile> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val raw   = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { MtsProfile.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }
    }

    fun saveProfiles(context: Context, profiles: List<MtsProfile>) {
        val arr = JSONArray()
        profiles.forEach { arr.put(it.toJson()) }
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putString(KEY_PROFILES, arr.toString()).commit()
    }

    fun getActiveProfile(context: Context): MtsProfile? {
        val id = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(KEY_ACTIVE_PROFILE, null) ?: return null
        return loadProfiles(context).find { it.id == id }
    }

    fun setActiveProfile(context: Context, profile: MtsProfile) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putString(KEY_ACTIVE_PROFILE, profile.id).commit()
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    fun addProfile(context: Context, name: String, color: String,
                   isKids: Boolean, pin: String? = null): MtsProfile? {
        val list = loadProfiles(context).toMutableList()
        if (list.size >= MAX_PROFILES) return null
        val profile = MtsProfile(
            id          = "prof_" + UUID.randomUUID().toString().replace("-","").take(8),
            name        = name.trim().take(20),
            avatarColor = color,
            isKids      = isKids,
            pinHash     = pin?.let { MtsProfile.sha256(it) },
            createdAt   = System.currentTimeMillis()
        )
        list.add(profile)
        saveProfiles(context, list)
        return profile
    }

    fun updateProfile(context: Context, updated: MtsProfile) {
        val list = loadProfiles(context).toMutableList()
        val idx  = list.indexOfFirst { it.id == updated.id }
        if (idx >= 0) { list[idx] = updated; saveProfiles(context, list) }
    }

    fun deleteProfile(context: Context, profileId: String) {
        val list = loadProfiles(context).filter { it.id != profileId }
        saveProfiles(context, list)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.getString(KEY_ACTIVE_PROFILE, null) == profileId)
            prefs.edit().remove(KEY_ACTIVE_PROFILE).commit()
    }

    // ── Kids Mode ─────────────────────────────────────────────────────────────

    fun isKidsModeActive(context: Context): Boolean =
        getActiveProfile(context)?.isKids ?: false

    // ── Cloud Sync key ────────────────────────────────────────────────────────

    fun cloudSyncKey(email: String, profileId: String): String {
        val safeEmail = email.replace(Regex("[^A-Za-z0-9]"), "_")
        return "mtsflix_sync_${safeEmail}_${profileId}.json"
    }
}
