package com.mts.mtsflix.watchlist

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * MTSFlix Watchlist Manager v1.1.5
 * Menguruskan senarai tonton (watchlist) pengguna untuk notifikasi episod baru.
 */
object WatchlistManager {

    private const val KEY_WATCHLIST = "mtsflix_watchlist"

    data class WatchlistItem(
        val name: String,
        val url: String,
        val apiName: String,
        val posterUrl: String = "",
        val lastKnownEpisodeCount: Int = 0,
        val addedAt: Long = System.currentTimeMillis()
    )

    fun getWatchlist(context: Context): List<WatchlistItem> {
        val json = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(KEY_WATCHLIST, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull {
                try {
                    val obj = arr.getJSONObject(it)
                    WatchlistItem(
                        name = obj.getString("name"),
                        url = obj.getString("url"),
                        apiName = obj.getString("apiName"),
                        posterUrl = obj.optString("posterUrl", ""),
                        lastKnownEpisodeCount = obj.optInt("lastKnownEpisodeCount", 0),
                        addedAt = obj.optLong("addedAt", System.currentTimeMillis())
                    )
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addToWatchlist(context: Context, item: WatchlistItem) {
        val current = getWatchlist(context).toMutableList()
        if (current.none { it.url == item.url }) {
            current.add(item)
            saveWatchlist(context, current)
        }
    }

    fun removeFromWatchlist(context: Context, url: String) {
        val current = getWatchlist(context).filter { it.url != url }
        saveWatchlist(context, current)
    }

    fun isInWatchlist(context: Context, url: String): Boolean {
        return getWatchlist(context).any { it.url == url }
    }

    fun updateLastEpisodeCount(context: Context, url: String, newCount: Int) {
        val current = getWatchlist(context).map {
            if (it.url == url) it.copy(lastKnownEpisodeCount = newCount) else it
        }
        saveWatchlist(context, current)
    }

    private fun saveWatchlist(context: Context, list: List<WatchlistItem>) {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(JSONObject().apply {
                put("name", item.name)
                put("url", item.url)
                put("apiName", item.apiName)
                put("posterUrl", item.posterUrl)
                put("lastKnownEpisodeCount", item.lastKnownEpisodeCount)
                put("addedAt", item.addedAt)
            })
        }
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putString(KEY_WATCHLIST, arr.toString()).apply()
    }

    // --- Google Account Backup Support ---
    fun exportBackupJson(context: Context): String {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getString(KEY_WATCHLIST, "[]") ?: "[]"
    }

    fun importBackupJson(context: Context, json: String) {
        try {
            JSONArray(json) // validate
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putString(KEY_WATCHLIST, json).apply()
        } catch (e: Exception) { /* ignore */ }
    }
}
