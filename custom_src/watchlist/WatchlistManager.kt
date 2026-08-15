package com.mts.mtsflix.watchlist

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * MTSFlix Watchlist Manager v1.1.5 (Updated)
 * - Membaca terus dari sejarah tontonan CloudStream (DataStoreHelper)
 * - Toggle notifikasi per-show (🔔/🔕) & per-provider
 * - Simpan kiraan episod terakhir untuk kesan episod baru
 */
object WatchlistManager {

    // Keys for manual watchlist
    private const val KEY_WATCHLIST = "mtsflix_watchlist"

    // Keys for notification preferences
    const val KEY_NOTIF_ENABLED_URLS      = "mtsflix_notif_enabled_urls"
    const val KEY_NOTIF_DISABLED_PROVIDERS = "mtsflix_notif_disabled_providers"
    const val KEY_HISTORY_EP_COUNTS       = "mtsflix_history_ep_counts"

    // --- Data classes ---

    data class WatchlistItem(
        val name: String,
        val url: String,
        val apiName: String,
        val posterUrl: String = "",
        val lastKnownEpisodeCount: Int = 0,
        val addedAt: Long = System.currentTimeMillis()
    )

    /** Item dari sejarah tontonan / bookmark DataStoreHelper */
    data class WatchHistoryItem(
        val name: String,
        val url: String,
        val apiName: String,
        val posterUrl: String = "",
        val id: Int? = null
    )

    // --- Read from CloudStream DataStoreHelper watch history ---

    /**
     * Baca semua item dari sejarah tontonan (bookmarks + resume watching).
     * Gabungkan dan buang duplikat berdasarkan URL.
     * Boleh dipanggil dari Thread biasa (bukan suspend).
     */
    fun getHistoryFromDataStore(): List<WatchHistoryItem> {
        val result = mutableListOf<WatchHistoryItem>()
        val seenUrls = mutableSetOf<String>()

        try {
            // 1. Bookmark items (shows that user bookmarked/added to library)
            val bookmarked = com.lagradost.cloudstream3.utils.DataStoreHelper.getAllBookmarkedData()
            for (item in bookmarked) {
                if (item.url.isNotBlank() && seenUrls.add(item.url)) {
                    result.add(
                        WatchHistoryItem(
                            name = item.name,
                            url = item.url,
                            apiName = item.apiName,
                            posterUrl = item.posterUrl ?: "",
                            id = item.id
                        )
                    )
                }
            }
        } catch (e: Exception) { /* silent */ }

        try {
            // 2. Resume watching items (recently watched episodes)
            val resumeIds = com.lagradost.cloudstream3.utils.DataStoreHelper.getAllResumeStateIds()
            resumeIds?.forEach { parentId ->
                try {
                    val cached = com.lagradost.cloudstream3.CloudStreamApp.getKey<com.lagradost.cloudstream3.utils.downloader.DownloadObjects.DownloadHeaderCached>(
                        com.lagradost.cloudstream3.utils.DOWNLOAD_HEADER_CACHE,
                        parentId.toString()
                    )
                    if (cached != null && cached.url.isNotBlank() && seenUrls.add(cached.url)) {
                        result.add(
                            WatchHistoryItem(
                                name = cached.name,
                                url = cached.url,
                                apiName = cached.apiName,
                                posterUrl = cached.poster ?: "",
                                id = parentId
                            )
                        )
                    }
                } catch (e: Exception) { /* skip this item */ }
            }
        } catch (e: Exception) { /* silent */ }

        return result
    }

    // --- Per-show notification toggle ---

    fun isShowNotificationEnabled(context: Context, url: String): Boolean {
        val enabled = PreferenceManager.getDefaultSharedPreferences(context)
            .getStringSet(KEY_NOTIF_ENABLED_URLS, emptySet()) ?: emptySet()
        return enabled.contains(url)
    }

    fun setShowNotificationEnabled(context: Context, url: String, enabled: Boolean) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val current = prefs.getStringSet(KEY_NOTIF_ENABLED_URLS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (enabled) current.add(url) else current.remove(url)
        prefs.edit().putStringSet(KEY_NOTIF_ENABLED_URLS, current).apply()
    }

    fun getEnabledNotificationUrls(context: Context): Set<String> {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getStringSet(KEY_NOTIF_ENABLED_URLS, emptySet()) ?: emptySet()
    }

    // --- Per-provider notification toggle ---

    fun isProviderNotificationEnabled(context: Context, apiName: String): Boolean {
        val disabled = PreferenceManager.getDefaultSharedPreferences(context)
            .getStringSet(KEY_NOTIF_DISABLED_PROVIDERS, emptySet()) ?: emptySet()
        return !disabled.contains(apiName)
    }

    fun setProviderNotificationEnabled(context: Context, apiName: String, enabled: Boolean) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val disabled = prefs.getStringSet(KEY_NOTIF_DISABLED_PROVIDERS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (!enabled) disabled.add(apiName) else disabled.remove(apiName)
        prefs.edit().putStringSet(KEY_NOTIF_DISABLED_PROVIDERS, disabled).apply()
    }

    fun getDisabledProviders(context: Context): Set<String> {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getStringSet(KEY_NOTIF_DISABLED_PROVIDERS, emptySet()) ?: emptySet()
    }

    // --- Episode count tracking ---

    fun getLastEpisodeCount(context: Context, url: String): Int {
        val json = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(KEY_HISTORY_EP_COUNTS, "{}") ?: "{}"
        return try { JSONObject(json).optInt(url, 0) } catch (e: Exception) { 0 }
    }

    fun setLastEpisodeCount(context: Context, url: String, count: Int) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val json = prefs.getString(KEY_HISTORY_EP_COUNTS, "{}") ?: "{}"
        val obj = try { JSONObject(json) } catch (e: Exception) { JSONObject() }
        obj.put(url, count)
        prefs.edit().putString(KEY_HISTORY_EP_COUNTS, obj.toString()).apply()
    }

    // --- Manual watchlist (for backward compat) ---

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
        } catch (e: Exception) { emptyList() }
    }

    fun isInWatchlist(context: Context, url: String): Boolean =
        getWatchlist(context).any { it.url == url }

    // --- Google Account Backup ---

    fun exportBackupJson(context: Context): JSONObject {
        val json = JSONObject()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        json.put("enabled_urls", JSONArray(prefs.getStringSet(KEY_NOTIF_ENABLED_URLS, emptySet())?.toList() ?: emptyList<String>()))
        json.put("disabled_providers", JSONArray(prefs.getStringSet(KEY_NOTIF_DISABLED_PROVIDERS, emptySet())?.toList() ?: emptyList<String>()))
        json.put("ep_counts", prefs.getString(KEY_HISTORY_EP_COUNTS, "{}"))
        json.put("manual_watchlist", prefs.getString(KEY_WATCHLIST, "[]"))
        return json
    }

    fun importBackupJson(context: Context, json: JSONObject?) {
        if (json == null) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context).edit()
        json.optJSONArray("enabled_urls")?.let { arr ->
            prefs.putStringSet(KEY_NOTIF_ENABLED_URLS, (0 until arr.length()).map { arr.getString(it) }.toSet())
        }
        json.optJSONArray("disabled_providers")?.let { arr ->
            prefs.putStringSet(KEY_NOTIF_DISABLED_PROVIDERS, (0 until arr.length()).map { arr.getString(it) }.toSet())
        }
        json.optString("ep_counts").takeIf { it.isNotBlank() }?.let { prefs.putString(KEY_HISTORY_EP_COUNTS, it) }
        json.optString("manual_watchlist").takeIf { it.isNotBlank() }?.let { prefs.putString(KEY_WATCHLIST, it) }
        prefs.apply()
    }
}
