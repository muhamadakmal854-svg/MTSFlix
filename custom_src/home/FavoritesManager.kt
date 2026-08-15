package com.mts.mtsflix.home

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray

/**
 * MTSFlix Favorites Provider Manager v1.1.5
 * Menyimpan dan mengurus senarai Provider kegemaran untuk Quick Bar di Home Screen.
 */
object FavoritesManager {

    private const val KEY_FAVORITES = "mtsflix_fav_providers"
    private const val MAX_FAVORITES = 8

    fun getFavoriteProviders(context: Context): List<String> {
        val json = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(KEY_FAVORITES, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addFavorite(context: Context, name: String) {
        val current = getFavoriteProviders(context).toMutableList()
        if (!current.contains(name)) {
            if (current.size >= MAX_FAVORITES) current.removeAt(0) // buang yang lama jika penuh
            current.add(name)
            saveFavorites(context, current)
        }
    }

    fun removeFavorite(context: Context, name: String) {
        val current = getFavoriteProviders(context).toMutableList()
        current.remove(name)
        saveFavorites(context, current)
    }

    fun toggleFavorite(context: Context, name: String): Boolean {
        return if (isFavorite(context, name)) {
            removeFavorite(context, name)
            false
        } else {
            addFavorite(context, name)
            true
        }
    }

    fun isFavorite(context: Context, name: String): Boolean {
        return getFavoriteProviders(context).contains(name)
    }

    fun clearAll(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putString(KEY_FAVORITES, "[]").apply()
    }

    private fun saveFavorites(context: Context, list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putString(KEY_FAVORITES, arr.toString()).apply()
    }

    // --- Google Account Backup Support ---
    fun exportBackupJson(context: Context): String {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getString(KEY_FAVORITES, "[]") ?: "[]"
    }

    fun importBackupJson(context: Context, json: String) {
        try {
            JSONArray(json) // validate JSON
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putString(KEY_FAVORITES, json).apply()
        } catch (e: Exception) { /* ignore invalid */ }
    }
}
