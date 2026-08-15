package com.mts.mtsflix.watchlist

import android.content.Context
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import org.json.JSONArray

/**
 * MTSFlix Content Discovery Manager v1.1.5
 * Menguruskan snapshot katalog provider dan pengesanan kandungan baru.
 * Menyokong 4 track: Episod Baru, Filem Baru, Siri Baru, Anime/Drama Asia Baru.
 */
object ContentDiscoveryManager {

    // Snapshot storage keys (per provider)
    private const val SNAP_PREFIX    = "mtsflix_snap_"
    private const val MAX_SNAP_SIZE  = 300

    // Notification type preference keys
    const val PREF_NOTIF_NEW_EPISODES = "mtsflix_notif_new_episodes"
    const val PREF_NOTIF_NEW_MOVIES   = "mtsflix_notif_new_movies"
    const val PREF_NOTIF_NEW_SERIES   = "mtsflix_notif_new_series"
    const val PREF_NOTIF_NEW_ANIME    = "mtsflix_notif_new_anime"
    const val PREF_NOTIF_NEW_ASIAN    = "mtsflix_notif_new_asian"

    // TvType groupings
    val MOVIE_TYPES: Set<TvType>  = setOf(TvType.Movie, TvType.AnimeMovie, TvType.Documentary)
    val SERIES_TYPES: Set<TvType> = setOf(TvType.TvSeries, TvType.Cartoon, TvType.Live)
    val ANIME_TYPES: Set<TvType>  = setOf(TvType.Anime, TvType.OVA)
    val ASIAN_TYPES: Set<TvType>  = setOf(TvType.AsianDrama)

    // --- Snapshot Management ---

    fun getSnapshot(context: Context, apiName: String): Set<String> {
        val json = PreferenceManager.getDefaultSharedPreferences(context)
            .getString("$SNAP_PREFIX${apiName.hashCode()}", "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } catch (e: Exception) { emptySet() }
    }

    fun saveSnapshot(context: Context, apiName: String, urls: Collection<String>) {
        val limited = urls.take(MAX_SNAP_SIZE)
        val arr = JSONArray(); limited.forEach { arr.put(it) }
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putString("$SNAP_PREFIX${apiName.hashCode()}", arr.toString()).apply()
    }

    /**
     * Bandingkan snapshot lama dengan item baru dari API.
     * @return senarai item yang BARU (tidak ada dalam snapshot lama)
     * @note Jika snapshot kosong (first run), pulangkan senarai kosong untuk elak spam
     */
    fun findNewItems(oldSnapshot: Set<String>, currentItems: List<SearchResponse>): List<SearchResponse> {
        if (oldSnapshot.isEmpty()) return emptyList() // first run — hanya simpan snapshot
        return currentItems.filter { !oldSnapshot.contains(it.url) }
    }

    // --- Notification Type Preferences ---

    fun isNewEpisodesEnabled(context: Context) =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_NOTIF_NEW_EPISODES, true)

    fun isNewMoviesEnabled(context: Context) =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_NOTIF_NEW_MOVIES, true)

    fun isNewSeriesEnabled(context: Context) =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_NOTIF_NEW_SERIES, true)

    fun isNewAnimeEnabled(context: Context) =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_NOTIF_NEW_ANIME, true)

    fun isNewAsianEnabled(context: Context) =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_NOTIF_NEW_ASIAN, true)

    // --- Type Helpers ---

    fun getTypeEmoji(type: TvType?): String = when (type) {
        TvType.Movie         -> "🎬"
        TvType.AnimeMovie    -> "🎌"
        TvType.TvSeries      -> "📺"
        TvType.Cartoon       -> "🎨"
        TvType.Anime         -> "🇯🇵"
        TvType.OVA           -> "🇯🇵"
        TvType.AsianDrama    -> "🇨🇳"
        TvType.Documentary   -> "🎞️"
        TvType.Live          -> "📡"
        else                 -> "🆕"
    }

    fun getTypeLabel(type: TvType?): String = when (type) {
        TvType.Movie         -> "Filem"
        TvType.AnimeMovie    -> "Filem Anime"
        TvType.TvSeries      -> "Siri TV"
        TvType.Cartoon       -> "Kartun"
        TvType.Anime         -> "Anime"
        TvType.OVA           -> "Anime OVA"
        TvType.AsianDrama    -> "Drama Asia"
        TvType.Documentary   -> "Dokumentari"
        TvType.Live          -> "Siaran Langsung"
        else                 -> "Kandungan"
    }

    fun getCategoryNotifTitle(type: TvType?): String = when {
        type in MOVIE_TYPES  -> "🎬 Filem Baru"
        type in SERIES_TYPES -> "📺 Siri TV Baru"
        type in ANIME_TYPES  -> "🇯🇵 Anime Baru"
        type in ASIAN_TYPES  -> "🇨🇳 Drama Asia Baru"
        else                 -> "🆕 Kandungan Baru"
    }
}
