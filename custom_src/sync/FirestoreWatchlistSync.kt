package com.mts.mtsflix.sync

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

private const val TAG = "MTSFlix:Firestore"

// ─── Data Models ──────────────────────────────────────────────────────────────

/**
 * Represents a show/movie in user's watchlist
 */
data class WatchlistItem(
    val id: String = "",
    val title: String = "",
    val url: String = "",
    val posterUrl: String? = null,
    val type: String = "Movie",        // "Movie", "TvSeries", "Anime", etc.
    val ongoing: Boolean = false,       // true = still airing/ongoing
    val currentEpisode: Long = 0,       // Latest episode count (updated by user/tracker)
    val lastNotifiedEpisode: Long = 0,  // Last episode user was notified about
    val addedAt: Long = System.currentTimeMillis(),
    val lastWatched: Long = System.currentTimeMillis()
)

/**
 * Represents a watch history entry (per episode)
 */
data class WatchHistoryItem(
    val id: String = "",
    val title: String = "",
    val url: String = "",
    val posterUrl: String? = null,
    val episode: Int = 0,
    val season: Int = 1,
    val position: Long = 0,            // Playback position in milliseconds
    val duration: Long = 0,            // Total duration in milliseconds
    val completed: Boolean = false,
    val watchedAt: Long = System.currentTimeMillis()
)

// ─── Firestore Sync Manager ───────────────────────────────────────────────────

/**
 * MTSFlix Firestore Sync Manager
 * Handles watchlist and watch history sync to/from Firebase Firestore
 * 
 * Firestore Structure:
 * users/{uid}/
 *   ├── profile         (display name, email, photo)
 *   ├── watchlist/{id}  (WatchlistItem)
 *   └── history/{id}    (WatchHistoryItem)
 */
object FirestoreWatchlistSync {

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private val userId: String? get() = auth.currentUser?.uid

    private fun userDoc() = userId?.let { db.collection("users").document(it) }

    // ─── Watchlist: Add ──────────────────────────────────────────────────────

    fun addToWatchlist(item: WatchlistItem, onResult: (Boolean) -> Unit = {}) {
        val user = userDoc() ?: run { onResult(false); return }
        val docId = generateId(item.url)

        user.collection("watchlist")
            .document(docId)
            .set(item.copy(id = docId), SetOptions.merge())
            .addOnSuccessListener {
                Log.i(TAG, "✅ Added to watchlist: ${item.title}")
                onResult(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to add to watchlist: ${e.message}")
                onResult(false)
            }
    }

    // ─── Watchlist: Remove ───────────────────────────────────────────────────

    fun removeFromWatchlist(url: String, onResult: (Boolean) -> Unit = {}) {
        val user = userDoc() ?: run { onResult(false); return }
        val docId = generateId(url)

        user.collection("watchlist")
            .document(docId)
            .delete()
            .addOnSuccessListener {
                Log.i(TAG, "✅ Removed from watchlist: $url")
                onResult(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to remove from watchlist: ${e.message}")
                onResult(false)
            }
    }

    // ─── Watchlist: Check ────────────────────────────────────────────────────

    fun isInWatchlist(url: String, onResult: (Boolean) -> Unit) {
        val user = userDoc() ?: run { onResult(false); return }
        val docId = generateId(url)

        user.collection("watchlist")
            .document(docId)
            .get()
            .addOnSuccessListener { onResult(it.exists()) }
            .addOnFailureListener { onResult(false) }
    }

    suspend fun isInWatchlistAsync(url: String): Boolean {
        val user = userDoc() ?: return false
        val docId = generateId(url)
        return try {
            user.collection("watchlist").document(docId).get().await().exists()
        } catch (e: Exception) { false }
    }

    // ─── Watchlist: Get All ───────────────────────────────────────────────────

    fun getWatchlist(onResult: (List<WatchlistItem>) -> Unit) {
        val user = userDoc() ?: run { onResult(emptyList()); return }

        user.collection("watchlist")
            .orderBy("lastWatched", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { doc ->
                    try { doc.toObject(WatchlistItem::class.java) } catch (e: Exception) { null }
                }
                Log.d(TAG, "Watchlist loaded: ${list.size} items")
                onResult(list)
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    suspend fun getWatchlistAsync(): List<WatchlistItem> {
        val user = userDoc() ?: return emptyList()
        return try {
            user.collection("watchlist")
                .orderBy("lastWatched", Query.Direction.DESCENDING)
                .get().await()
                .documents.mapNotNull { doc ->
                    try { doc.toObject(WatchlistItem::class.java) } catch (e: Exception) { null }
                }
        } catch (e: Exception) { emptyList() }
    }

    // ─── Watchlist: Update Episode Count ─────────────────────────────────────

    fun updateEpisodeCount(url: String, episodeCount: Long, onResult: (Boolean) -> Unit = {}) {
        val user = userDoc() ?: run { onResult(false); return }
        val docId = generateId(url)

        user.collection("watchlist").document(docId)
            .update(mapOf(
                "currentEpisode" to episodeCount,
                "lastWatched" to System.currentTimeMillis()
            ))
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    // ─── Watch History: Save ─────────────────────────────────────────────────

    fun saveWatchHistory(item: WatchHistoryItem, onResult: (Boolean) -> Unit = {}) {
        val user = userDoc() ?: run { onResult(false); return }
        val docId = "${generateId(item.url)}_s${item.season}e${item.episode}"

        val data = item.copy(
            id = docId,
            watchedAt = System.currentTimeMillis()
        )

        user.collection("history")
            .document(docId)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "History saved: ${item.title} S${item.season}E${item.episode}")
                onResult(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save history: ${e.message}")
                onResult(false)
            }
    }

    // ─── Watch History: Get Progress ─────────────────────────────────────────

    /**
     * Returns (position, duration) in milliseconds for a specific episode
     */
    fun getWatchProgress(url: String, episode: Int, season: Int = 1,
                         onResult: (position: Long, duration: Long) -> Unit) {
        val user = userDoc() ?: run { onResult(0, 0); return }
        val docId = "${generateId(url)}_s${season}e$episode"

        user.collection("history")
            .document(docId)
            .get()
            .addOnSuccessListener { doc ->
                val position = doc.getLong("position") ?: 0L
                val duration = doc.getLong("duration") ?: 0L
                onResult(position, duration)
            }
            .addOnFailureListener { onResult(0, 0) }
    }

    suspend fun getWatchProgressAsync(url: String, episode: Int, season: Int = 1): Pair<Long, Long> {
        val user = userDoc() ?: return Pair(0L, 0L)
        val docId = "${generateId(url)}_s${season}e$episode"
        return try {
            val doc = user.collection("history").document(docId).get().await()
            val pos = doc.getLong("position") ?: 0L
            val dur = doc.getLong("duration") ?: 0L
            Pair(pos, dur)
        } catch (e: Exception) { Pair(0L, 0L) }
    }

    // ─── Watch History: Get Recent ────────────────────────────────────────────

    fun getRecentHistory(limit: Long = 50, onResult: (List<WatchHistoryItem>) -> Unit) {
        val user = userDoc() ?: run { onResult(emptyList()); return }

        user.collection("history")
            .orderBy("watchedAt", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { doc ->
                    try { doc.toObject(WatchHistoryItem::class.java) } catch (e: Exception) { null }
                }
                onResult(list)
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    // ─── User Profile ─────────────────────────────────────────────────────────

    fun saveUserProfile(displayName: String?, email: String?, photoUrl: String?) {
        val user = userDoc() ?: return

        val profile = hashMapOf(
            "displayName" to displayName,
            "email" to email,
            "photoUrl" to photoUrl,
            "appVersion" to "MTSFlix",
            "lastSeen" to System.currentTimeMillis()
        )

        user.set(profile, SetOptions.merge())
            .addOnSuccessListener { Log.i(TAG, "✅ User profile saved") }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to save profile: ${e.message}") }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun generateId(url: String): String {
        return url.replace(Regex("[^a-zA-Z0-9]"), "_").take(100)
    }
}
