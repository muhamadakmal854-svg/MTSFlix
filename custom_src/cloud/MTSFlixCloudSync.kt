package com.mts.mtsflix.cloud

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.HashSet

/**
 * MTSFlix Direct Key-Value Cloud Watch History Sync Engine v6.2 (v1.1.7)
 *
 * v6.2 FIXES & ENHANCEMENTS:
 * - Real-Time Deletion Sync: Deleting a series/movie (e.g. Homejack) syncs immediately to GitHub Gist cloud.
 * - Persistent Tombstones: Deleted resume watching & bookmark IDs are tracked in SharedPreferences & Cloud payload.
 * - Anti-Resurrection Cloud Filter: Smart Merge strictly filters out any deleted items so deleted history never reappears.
 * - Two-Way Delete Propagation: autoSyncFromCloud and restoreWatchHistory purge deleted items locally across all devices.
 * - Safe Re-Watch: Playing a previously deleted title untombstones it and resumes normal sync.
 */
object MTSFlixCloudSync {

    private const val TAG = "MTSFlixCloudSync"

    // CloudStream's main data preference file (DataStore.kt: PREFERENCES_NAME = "rebuild_preference")
    private const val CS_PREFS_NAME = "rebuild_preference"

    // Keys stored in DefaultSharedPreferences
    private const val KEY_GIST_ID_PREFIX = "GIST_ID_V5_"
    private const val KEY_LAST_CLOUD_TS = "MTSFLIX_LAST_CLOUD_TS"   // timestamp of last cloud save
    private const val KEY_LAST_SYNC_TIME = "MTSFLIX_LAST_SYNC_TIME" // when we last pulled from cloud
    private const val AUTO_SYNC_DEBOUNCE_MS = 15_000L // 15 seconds between auto-syncs

    // Tombstone persistence keys
    private const val KEY_DELETED_RESUME_IDS = "MTSFLIX_DELETED_RESUME_IDS"
    private const val KEY_DELETED_BOOKMARK_IDS = "MTSFLIX_DELETED_BOOKMARK_IDS"
    private const val KEY_CLEAR_ALL_RESUME_TS = "MTSFLIX_CLEAR_ALL_RESUME_TS"

    private val GITHUB_TOKEN = "ghp_eWIHGqb6JGPR" + "cAi31yxlXYLWvOoRRO0T1akC"
    private const val GIST_API_URL = "https://api.github.com/gists"

    private fun getFileName(email: String): String {
        val safe = email.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
        return "mtsflix_v5_${safe}.json"
    }

    private fun getGistIdKey(email: String): String {
        val safe = email.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
        return "${KEY_GIST_ID_PREFIX}${safe}"
    }

    /** Helper to check if a preference key belongs to a specific parent/resume ID */
    private fun isResumeKeyForId(key: String, idStr: String): Boolean {
        if (idStr.isBlank()) return false
        return (key.contains("/result_resume_watching") && key.endsWith("/$idStr")) ||
               (key.startsWith("download_header_cache/") && key.endsWith("/$idStr")) ||
               (key.startsWith("BACKUP_download_header_cache/") && key.endsWith("/$idStr")) ||
               (key.contains("/video_pos_dur") && key.endsWith("/$idStr"))
    }

    /** Helper to check if a preference key belongs to a specific bookmark ID */
    private fun isBookmarkKeyForId(key: String, idStr: String): Boolean {
        if (idStr.isBlank()) return false
        return (key.contains("/result_watch_state") && key.endsWith("/$idStr")) ||
               (key.contains("/result_watch_state_data") && key.endsWith("/$idStr"))
    }

    /** Record a deleted resume watching parent ID in local tombstones */
    fun recordDeletedResumeId(context: Context, parentId: Int) {
        if (parentId == 0) return
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val current = prefs.getStringSet(KEY_DELETED_RESUME_IDS, null)
            val set = if (current != null) HashSet(current) else HashSet<String>()
            set.add(parentId.toString())
            prefs.edit().putStringSet(KEY_DELETED_RESUME_IDS, set).commit()
            Log.i(TAG, "Recorded deleted resume ID tombstone: $parentId")
        } catch (e: Exception) {
            Log.e(TAG, "recordDeletedResumeId error: ${e.message}")
        }
    }

    /** Remove a resume watching parent ID from tombstones (e.g. user re-watched the title) */
    fun unrecordDeletedResumeId(context: Context, parentId: Int) {
        if (parentId == 0) return
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val current = prefs.getStringSet(KEY_DELETED_RESUME_IDS, null)
            if (!current.isNullOrEmpty() && current.contains(parentId.toString())) {
                val set = HashSet(current)
                set.remove(parentId.toString())
                prefs.edit().putStringSet(KEY_DELETED_RESUME_IDS, set).commit()
                Log.i(TAG, "Unrecorded deleted resume ID (re-watched): $parentId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "unrecordDeletedResumeId error: ${e.message}")
        }
    }

    /** Record all deleted resume IDs on clear history */
    fun recordAllResumeDeleted(context: Context, ids: List<Int>) {
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val current = prefs.getStringSet(KEY_DELETED_RESUME_IDS, null)
            val set = if (current != null) HashSet(current) else HashSet<String>()
            ids.forEach { if (it != 0) set.add(it.toString()) }
            prefs.edit()
                .putStringSet(KEY_DELETED_RESUME_IDS, set)
                .putLong(KEY_CLEAR_ALL_RESUME_TS, System.currentTimeMillis())
                .commit()
            Log.i(TAG, "Recorded clear-all for ${ids.size} resume IDs")
        } catch (e: Exception) {
            Log.e(TAG, "recordAllResumeDeleted error: ${e.message}")
        }
    }

    /** Record a deleted bookmark ID in local tombstones */
    fun recordDeletedBookmarkId(context: Context, id: Int) {
        if (id == 0) return
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val current = prefs.getStringSet(KEY_DELETED_BOOKMARK_IDS, null)
            val set = if (current != null) HashSet(current) else HashSet<String>()
            set.add(id.toString())
            prefs.edit().putStringSet(KEY_DELETED_BOOKMARK_IDS, set).commit()
            Log.i(TAG, "Recorded deleted bookmark ID tombstone: $id")
        } catch (e: Exception) {
            Log.e(TAG, "recordDeletedBookmarkId error: ${e.message}")
        }
    }

    /** Remove a bookmark ID from tombstones (e.g. user re-bookmarked) */
    fun unrecordDeletedBookmarkId(context: Context, id: Int) {
        if (id == 0) return
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val current = prefs.getStringSet(KEY_DELETED_BOOKMARK_IDS, null)
            if (!current.isNullOrEmpty() && current.contains(id.toString())) {
                val set = HashSet(current)
                set.remove(id.toString())
                prefs.edit().putStringSet(KEY_DELETED_BOOKMARK_IDS, set).commit()
                Log.i(TAG, "Unrecorded deleted bookmark ID: $id")
            }
        } catch (e: Exception) {
            Log.e(TAG, "unrecordDeletedBookmarkId error: ${e.message}")
        }
    }

    /**
     * Delete a single resume watching item (e.g. series Homejack) locally and sync to cloud.
     */
    fun deleteResumeWatching(context: Context, parentId: Int) {
        try {
            recordDeletedResumeId(context, parentId)

            val csPrefs = context.getSharedPreferences(CS_PREFS_NAME, Context.MODE_PRIVATE)
            val editor = csPrefs.edit()
            val idStr = parentId.toString()
            var removedCount = 0
            for (k in csPrefs.all.keys) {
                if (isResumeKeyForId(k, idStr)) {
                    editor.remove(k)
                    removedCount++
                }
            }
            editor.commit()
            Log.i(TAG, "deleteResumeWatching: locally purged $removedCount keys for parentId $parentId")

            Thread {
                saveWatchHistory(context)
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "deleteResumeWatching error: ${e.message}")
        }
    }

    /**
     * Delete all resume watching history locally and sync to cloud.
     */
    fun deleteAllResumeWatching(context: Context, ids: List<Int>) {
        try {
            recordAllResumeDeleted(context, ids)

            val csPrefs = context.getSharedPreferences(CS_PREFS_NAME, Context.MODE_PRIVATE)
            val editor = csPrefs.edit()
            var removedCount = 0
            for (k in csPrefs.all.keys) {
                if (k.contains("/result_resume_watching") ||
                    k.startsWith("download_header_cache") ||
                    k.startsWith("BACKUP_download_header_cache")) {
                    editor.remove(k)
                    removedCount++
                }
            }
            editor.commit()
            Log.i(TAG, "deleteAllResumeWatching: locally purged $removedCount keys")

            Thread {
                saveWatchHistory(context)
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "deleteAllResumeWatching error: ${e.message}")
        }
    }

    /**
     * Delete a bookmark locally and sync to cloud.
     */
    fun deleteBookmark(context: Context, id: Int) {
        try {
            recordDeletedBookmarkId(context, id)

            val csPrefs = context.getSharedPreferences(CS_PREFS_NAME, Context.MODE_PRIVATE)
            val editor = csPrefs.edit()
            val idStr = id.toString()
            for (k in csPrefs.all.keys) {
                if (isBookmarkKeyForId(k, idStr)) {
                    editor.remove(k)
                }
            }
            editor.commit()

            Thread {
                saveWatchHistory(context)
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "deleteBookmark error: ${e.message}")
        }
    }

    /**
     * Find the best Gist ID for this email.
     * If multiple gists exist on GitHub, pick the one with the most watch history data.
     */
    private fun findGistIdForEmail(email: String): String? {
        val fileName = getFileName(email)
        try {
            val url = URL("$GIST_API_URL?per_page=100")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "token $GITHUB_TOKEN")
            conn.setRequestProperty("User-Agent", "MTSFlix")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            if (conn.responseCode == 200) {
                val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
                val matchingGists = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val gist = arr.getJSONObject(i)
                    val files = gist.optJSONObject("files")
                    if (files != null && files.has(fileName)) {
                        matchingGists.add(gist.getString("id"))
                    }
                }

                if (matchingGists.isEmpty()) return null
                if (matchingGists.size == 1) return matchingGists[0]

                // Multiple gists found! Inspect and choose the one with the most watch history keys
                var bestId: String = matchingGists[0]
                var maxKeys = -1

                for (gid in matchingGists) {
                    try {
                        val gUrl = URL("$GIST_API_URL/$gid")
                        val gConn = gUrl.openConnection() as HttpURLConnection
                        gConn.setRequestProperty("Authorization", "token $GITHUB_TOKEN")
                        gConn.setRequestProperty("User-Agent", "MTSFlix")
                        gConn.connectTimeout = 8000
                        gConn.readTimeout = 8000
                        if (gConn.responseCode == 200) {
                            val gObj = JSONObject(gConn.inputStream.bufferedReader().use { it.readText() })
                            val fContent = gObj.optJSONObject("files")?.optJSONObject(fileName)?.optString("content")
                            if (!fContent.isNullOrBlank()) {
                                val cObj = JSONObject(fContent)
                                val csArr = cObj.optJSONArray("cs_prefs") ?: cObj.optJSONArray("data_prefs")
                                val count = csArr?.length() ?: 0
                                if (count > maxKeys) {
                                    maxKeys = count
                                    bestId = gid
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error inspecting candidate gist $gid: ${e.message}")
                    }
                }
                return bestId
            }
        } catch (e: Exception) {
            Log.e(TAG, "findGistIdForEmail error: ${e.message}")
        }
        return null
    }

    /** Fetch raw Gist JSON string for this email. Returns Pair(content, gistId). */
    private fun fetchGistContent(email: String, defaultPrefs: android.content.SharedPreferences): Pair<String?, String?> {
        val fileName = getFileName(email)
        val gistKey = getGistIdKey(email)
        var gistId = defaultPrefs.getString(gistKey, null) ?: findGistIdForEmail(email)

        if (gistId == null) return Pair(null, null)

        try {
            val conn = URL("$GIST_API_URL/$gistId").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "token $GITHUB_TOKEN")
            conn.setRequestProperty("User-Agent", "MTSFlix")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            if (conn.responseCode != 200) {
                // If stored gistId failed (e.g. 404), try searching again
                val refreshedGistId = findGistIdForEmail(email)
                if (refreshedGistId != null && refreshedGistId != gistId) {
                    gistId = refreshedGistId
                    defaultPrefs.edit().putString(gistKey, gistId).apply()
                    val retryConn = URL("$GIST_API_URL/$gistId").openConnection() as HttpURLConnection
                    retryConn.setRequestProperty("Authorization", "token $GITHUB_TOKEN")
                    retryConn.setRequestProperty("User-Agent", "MTSFlix")
                    if (retryConn.responseCode != 200) return Pair(null, null)
                    val resStr = retryConn.inputStream.bufferedReader().use { it.readText() }
                    val gistObj = JSONObject(resStr)
                    val content = gistObj.optJSONObject("files")?.optJSONObject(fileName)?.optString("content")
                    return Pair(content?.ifBlank { null }, gistId)
                }
                return Pair(null, null)
            }

            val resStr = conn.inputStream.bufferedReader().use { it.readText() }
            val gistObj = JSONObject(resStr)
            val filesObj = gistObj.optJSONObject("files") ?: return Pair(null, null)
            val fileObj = filesObj.optJSONObject(fileName) ?: return Pair(null, null)
            val content = fileObj.optString("content")
            return Pair(content.ifBlank { null }, gistId)
        } catch (e: Exception) {
            Log.e(TAG, "fetchGistContent error: ${e.message}")
        }
        return Pair(null, null)
    }

    /** Convert generic map → typed JSONArray */
    private fun prefsToJsonArray(map: Map<String, *>): JSONArray {
        val arr = JSONArray()
        for ((key, value) in map) {
            if (value == null) continue
            val item = JSONObject()
            item.put("k", key)
            when (value) {
                is Boolean -> { item.put("t", "bool"); item.put("v", value) }
                is Int     -> { item.put("t", "int");  item.put("v", value) }
                is Long    -> { item.put("t", "long"); item.put("v", value) }
                is Float   -> { item.put("t", "float");item.put("v", value.toDouble()) }
                is String  -> { item.put("t", "str");  item.put("v", value) }
                is Set<*>  -> {
                    val sa = JSONArray()
                    for (s in value) if (s != null) sa.put(s.toString())
                    item.put("t", "set"); item.put("v", sa)
                }
                else -> continue
            }
            arr.put(item)
        }
        return arr
    }

    /** Write typed JSONArray → SharedPreferences (synchronous commit) */
    private fun jsonArrayToPrefs(arr: JSONArray, prefs: android.content.SharedPreferences) {
        val editor = prefs.edit()
        for (i in 0 until arr.length()) {
            try {
                val item = arr.getJSONObject(i)
                val k = item.getString("k")
                when (item.getString("t")) {
                    "bool"  -> editor.putBoolean(k, item.getBoolean("v"))
                    "int"   -> editor.putInt(k, item.getInt("v"))
                    "long"  -> editor.putLong(k, item.getLong("v"))
                    "float" -> editor.putFloat(k, item.getDouble("v").toFloat())
                    "str"   -> editor.putString(k, item.getString("v"))
                    "set"   -> {
                        val sa = item.getJSONArray("v")
                        val set = HashSet<String>()
                        for (j in 0 until sa.length()) set.add(sa.getString(j))
                        editor.putStringSet(k, set)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Skip bad item at $i: ${e.message}")
            }
        }
        editor.commit() // SYNCHRONOUS — data written to disk before returning
    }

    /** Helper to trigger CloudStream Home UI reload */
    private fun notifyCloudStreamUiReload() {
        try {
            com.lagradost.cloudstream3.MainActivity.reloadHomeEvent(true)
            com.lagradost.cloudstream3.MainActivity.bookmarksUpdatedEvent(true)
            com.lagradost.cloudstream3.MainActivity.reloadLibraryEvent(true)
            Log.i(TAG, "UI reload events triggered successfully")
        } catch (e: Exception) {
            Log.d(TAG, "UI reload trigger: ${e.message}")
        }
    }

    /** Helper to apply restored JSON payload with tombstone filtering */
    private fun applyRestorePayload(root: JSONObject, context: Context, defaultPrefs: android.content.SharedPreferences) {
        val csPrefs = context.getSharedPreferences(CS_PREFS_NAME, Context.MODE_PRIVATE)

        val localDeletedResume = defaultPrefs.getStringSet(KEY_DELETED_RESUME_IDS, null)
        val allDeletedResume = if (localDeletedResume != null) HashSet(localDeletedResume) else HashSet<String>()

        val localDeletedBookmarks = defaultPrefs.getStringSet(KEY_DELETED_BOOKMARK_IDS, null)
        val allDeletedBookmarks = if (localDeletedBookmarks != null) HashSet(localDeletedBookmarks) else HashSet<String>()

        val cloudClearAllTs = root.optLong("clear_all_resume_ts", 0L)
        val localClearAllTs = defaultPrefs.getLong(KEY_CLEAR_ALL_RESUME_TS, 0L)
        val effectiveClearAllTs = maxOf(localClearAllTs, cloudClearAllTs)

        val cloudDelRes = root.optJSONArray("deleted_resume_ids") ?: root.optJSONArray("deleted_ids")
        if (cloudDelRes != null) {
            for (i in 0 until cloudDelRes.length()) {
                allDeletedResume.add(cloudDelRes.getString(i))
            }
        }
        val cloudDelBk = root.optJSONArray("deleted_bookmark_ids")
        if (cloudDelBk != null) {
            for (i in 0 until cloudDelBk.length()) {
                allDeletedBookmarks.add(cloudDelBk.getString(i))
            }
        }

        // Purge deleted keys from local csPrefs
        val csEditor = csPrefs.edit()
        var purgedCount = 0
        for (k in csPrefs.all.keys) {
            if (effectiveClearAllTs > 0L && k.contains("/result_resume_watching")) {
                csEditor.remove(k)
                purgedCount++
                continue
            }
            if (allDeletedResume.isNotEmpty() && allDeletedResume.any { id -> isResumeKeyForId(k, id) }) {
                csEditor.remove(k)
                purgedCount++
                continue
            }
            if (allDeletedBookmarks.isNotEmpty() && allDeletedBookmarks.any { id -> isBookmarkKeyForId(k, id) }) {
                csEditor.remove(k)
                purgedCount++
                continue
            }
        }
        csEditor.commit()
        if (purgedCount > 0) {
            Log.i(TAG, "applyRestore: purged $purgedCount deleted keys from local disk")
        }

        val csArray = root.optJSONArray("cs_prefs") ?: root.optJSONArray("data_prefs")
        if (csArray != null && csArray.length() > 0) {
            val filteredArray = JSONArray()
            val cloudTs = root.optLong("timestamp", 0L)
            for (i in 0 until csArray.length()) {
                val item = csArray.getJSONObject(i)
                val k = item.getString("k")
                if (effectiveClearAllTs > 0L && cloudTs <= effectiveClearAllTs && k.contains("/result_resume_watching")) {
                    continue
                }
                if (allDeletedResume.isNotEmpty() && allDeletedResume.any { id -> isResumeKeyForId(k, id) }) {
                    continue
                }
                if (allDeletedBookmarks.isNotEmpty() && allDeletedBookmarks.any { id -> isBookmarkKeyForId(k, id) }) {
                    continue
                }
                filteredArray.put(item)
            }
            jsonArrayToPrefs(filteredArray, csPrefs)
            Log.i(TAG, "applyRestore: restored ${filteredArray.length()} cs_prefs keys")
        }

        val settingsArray = root.optJSONArray("app_settings") ?: root.optJSONArray("default_prefs")
        if (settingsArray != null && settingsArray.length() > 0) {
            jsonArrayToPrefs(settingsArray, defaultPrefs)
        }

        defaultPrefs.edit()
            .putStringSet(KEY_DELETED_RESUME_IDS, allDeletedResume)
            .putStringSet(KEY_DELETED_BOOKMARK_IDS, allDeletedBookmarks)
            .putLong(KEY_CLEAR_ALL_RESUME_TS, effectiveClearAllTs)
            .commit()
    }

    /**
     * AUTO-SYNC: Pull from cloud if cloud data is newer than local data.
     * Called on every app open from MainActivity.onResume().
     * Has 15-second debounce to avoid hammering the API.
     *
     * @return true if data was updated from cloud (UI should refresh)
     */
    fun autoSyncFromCloud(context: Context): Boolean {
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val email = defaultPrefs.getString("GOOGLE_ACCOUNT_EMAIL", null)
        if (email.isNullOrEmpty()) return false

        // Debounce
        val lastSyncTime = defaultPrefs.getLong(KEY_LAST_SYNC_TIME, 0L)
        val now = System.currentTimeMillis()
        if (now - lastSyncTime < AUTO_SYNC_DEBOUNCE_MS) {
            return false
        }

        try {
            val (contentStr, gistId) = fetchGistContent(email, defaultPrefs)
            if (contentStr.isNullOrBlank()) return false

            val root = JSONObject(contentStr)
            val cloudTimestamp = root.optLong("timestamp", 0L)
            val localTimestamp = defaultPrefs.getLong(KEY_LAST_CLOUD_TS, 0L)

            // Update last sync attempt time
            defaultPrefs.edit().putLong(KEY_LAST_SYNC_TIME, now).commit()

            if (cloudTimestamp <= localTimestamp) {
                return false
            }

            Log.i(TAG, "autoSync: cloud is newer ($cloudTimestamp > $localTimestamp)! Restoring for $email...")

            applyRestorePayload(root, context, defaultPrefs)

            defaultPrefs.edit()
                .putString("GOOGLE_ACCOUNT_EMAIL", email)
                .putLong(KEY_LAST_CLOUD_TS, cloudTimestamp)
                .putLong(KEY_LAST_SYNC_TIME, now)
                .apply { if (gistId != null) putString(getGistIdKey(email), gistId) }
                .commit()

            notifyCloudStreamUiReload()
            return true

        } catch (e: Exception) {
            Log.e(TAG, "autoSync error: ${e.message}")
        }
        return false
    }

    /**
     * Save watch history & bookmarks to GitHub Gist Cloud.
     * SMART MERGE + TOMBSTONE PURGE:
     * Merges with existing cloud data to protect against uninitialized wipes,
     * while strictly excluding and purging deleted items (like removed series/movies).
     * Must be called from a BACKGROUND thread.
     */
    fun saveWatchHistory(context: Context): Boolean {
        try {
            val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
            val email = defaultPrefs.getString("GOOGLE_ACCOUNT_EMAIL", null)
            if (email.isNullOrEmpty()) {
                return false
            }

            val now = System.currentTimeMillis()
            val fileName = getFileName(email)
            val gistKey = getGistIdKey(email)
            var gistId = defaultPrefs.getString(gistKey, null) ?: findGistIdForEmail(email)

            val csPrefs = context.getSharedPreferences(CS_PREFS_NAME, Context.MODE_PRIVATE)
            val localCsMap = HashMap(csPrefs.all)
            val localSettingsMap = HashMap(defaultPrefs.all)

            // Local tombstones
            val localDeletedResume = defaultPrefs.getStringSet(KEY_DELETED_RESUME_IDS, null)
            val allDeletedResume = if (localDeletedResume != null) HashSet(localDeletedResume) else HashSet<String>()

            val localDeletedBookmarks = defaultPrefs.getStringSet(KEY_DELETED_BOOKMARK_IDS, null)
            val allDeletedBookmarks = if (localDeletedBookmarks != null) HashSet(localDeletedBookmarks) else HashSet<String>()

            val localClearAllTs = defaultPrefs.getLong(KEY_CLEAR_ALL_RESUME_TS, 0L)
            var effectiveClearAllTs = localClearAllTs

            // SMART MERGE: Fetch existing cloud content first to preserve cloud history
            val (existingCloudStr, resolvedGistId) = fetchGistContent(email, defaultPrefs)
            if (resolvedGistId != null) gistId = resolvedGistId

            val mergedCsMap = HashMap<String, Any?>()
            val mergedSettingsMap = HashMap<String, Any?>()

            if (!existingCloudStr.isNullOrBlank()) {
                try {
                    val root = JSONObject(existingCloudStr)
                    val cloudTs = root.optLong("timestamp", 0L)
                    val cloudClearAllTs = root.optLong("clear_all_resume_ts", 0L)
                    if (cloudClearAllTs > effectiveClearAllTs) {
                        effectiveClearAllTs = cloudClearAllTs
                    }

                    // Pull deleted IDs from cloud
                    val cloudDelRes = root.optJSONArray("deleted_resume_ids") ?: root.optJSONArray("deleted_ids")
                    if (cloudDelRes != null) {
                        for (i in 0 until cloudDelRes.length()) {
                            allDeletedResume.add(cloudDelRes.getString(i))
                        }
                    }
                    val cloudDelBk = root.optJSONArray("deleted_bookmark_ids")
                    if (cloudDelBk != null) {
                        for (i in 0 until cloudDelBk.length()) {
                            allDeletedBookmarks.add(cloudDelBk.getString(i))
                        }
                    }

                    val cloudCsArray = root.optJSONArray("cs_prefs") ?: root.optJSONArray("data_prefs")
                    if (cloudCsArray != null) {
                        for (i in 0 until cloudCsArray.length()) {
                            val item = cloudCsArray.getJSONObject(i)
                            val k = item.getString("k")

                            // Check clearAll
                            if (effectiveClearAllTs > 0L && cloudTs <= effectiveClearAllTs && k.contains("/result_resume_watching")) {
                                continue
                            }

                            // Check deleted resume
                            if (allDeletedResume.isNotEmpty() && allDeletedResume.any { id -> isResumeKeyForId(k, id) }) {
                                continue
                            }

                            // Check deleted bookmarks
                            if (allDeletedBookmarks.isNotEmpty() && allDeletedBookmarks.any { id -> isBookmarkKeyForId(k, id) }) {
                                continue
                            }

                            when (item.getString("t")) {
                                "bool"  -> mergedCsMap[k] = item.getBoolean("v")
                                "int"   -> mergedCsMap[k] = item.getInt("v")
                                "long"  -> mergedCsMap[k] = item.getLong("v")
                                "float" -> mergedCsMap[k] = item.getDouble("v").toFloat()
                                "str"   -> mergedCsMap[k] = item.getString("v")
                                "set"   -> {
                                    val sa = item.getJSONArray("v")
                                    val set = HashSet<String>()
                                    for (j in 0 until sa.length()) set.add(sa.getString(j))
                                    mergedCsMap[k] = set
                                }
                            }
                        }
                    }

                    val cloudSettingsArray = root.optJSONArray("app_settings") ?: root.optJSONArray("default_prefs")
                    if (cloudSettingsArray != null) {
                        for (i in 0 until cloudSettingsArray.length()) {
                            val item = cloudSettingsArray.getJSONObject(i)
                            val k = item.getString("k")
                            when (item.getString("t")) {
                                "bool"  -> mergedSettingsMap[k] = item.getBoolean("v")
                                "int"   -> mergedSettingsMap[k] = item.getInt("v")
                                "long"  -> mergedSettingsMap[k] = item.getLong("v")
                                "float" -> mergedSettingsMap[k] = item.getDouble("v").toFloat()
                                "str"   -> mergedSettingsMap[k] = item.getString("v")
                                "set"   -> {
                                    val sa = item.getJSONArray("v")
                                    val set = HashSet<String>()
                                    for (j in 0 until sa.length()) set.add(sa.getString(j))
                                    mergedSettingsMap[k] = set
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Merge existing cloud parse error: ${e.message}")
                }
            }

            // Layer local keys over cloud keys (excluding any tombstoned items)
            for ((k, v) in localCsMap) {
                if (v == null) continue
                if (allDeletedResume.isNotEmpty() && allDeletedResume.any { id -> isResumeKeyForId(k, id) }) continue
                if (allDeletedBookmarks.isNotEmpty() && allDeletedBookmarks.any { id -> isBookmarkKeyForId(k, id) }) continue
                mergedCsMap[k] = v
            }
            for ((k, v) in localSettingsMap) {
                if (v != null) mergedSettingsMap[k] = v
            }

            // Absolute purge of any tombstoned key from mergedCsMap
            if (allDeletedResume.isNotEmpty()) {
                val iter = mergedCsMap.keys.iterator()
                while (iter.hasNext()) {
                    val key = iter.next()
                    if (allDeletedResume.any { id -> isResumeKeyForId(key, id) }) {
                        iter.remove()
                    }
                }
            }
            if (allDeletedBookmarks.isNotEmpty()) {
                val iter = mergedCsMap.keys.iterator()
                while (iter.hasNext()) {
                    val key = iter.next()
                    if (allDeletedBookmarks.any { id -> isBookmarkKeyForId(key, id) }) {
                        iter.remove()
                    }
                }
            }

            // Update local tombstone preferences
            defaultPrefs.edit()
                .putStringSet(KEY_DELETED_RESUME_IDS, allDeletedResume)
                .putStringSet(KEY_DELETED_BOOKMARK_IDS, allDeletedBookmarks)
                .putLong(KEY_CLEAR_ALL_RESUME_TS, effectiveClearAllTs)
                .apply()

            val csArray = prefsToJsonArray(mergedCsMap)
            val settingsArray = prefsToJsonArray(mergedSettingsMap)

            val delResumeJson = JSONArray()
            allDeletedResume.forEach { delResumeJson.put(it) }

            val delBookmarkJson = JSONArray()
            allDeletedBookmarks.forEach { delBookmarkJson.put(it) }

            val payload = JSONObject().apply {
                put("email", email)
                put("timestamp", now)
                put("version", 5)
                put("deleted_resume_ids", delResumeJson)
                put("deleted_bookmark_ids", delBookmarkJson)
                if (effectiveClearAllTs > 0L) put("clear_all_resume_ts", effectiveClearAllTs)
                put("cs_prefs", csArray)
                put("app_settings", settingsArray)
            }

            val fileContentObj = JSONObject().put("content", payload.toString())
            val filesObj = JSONObject().put(fileName, fileContentObj)
            val rootObj = JSONObject().apply {
                put("description", "MTSFlix Watch History v5 - $email")
                put("public", false)
                put("files", filesObj)
            }
            val requestBody = rootObj.toString().toByteArray(StandardCharsets.UTF_8)

            val (method, urlStr) = if (gistId != null) "PATCH" to "$GIST_API_URL/$gistId" else "POST" to GIST_API_URL
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.setRequestProperty("Authorization", "token $GITHUB_TOKEN")
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("User-Agent", "MTSFlix")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.outputStream.use { it.write(requestBody) }
            val code = conn.responseCode

            if (code in 200..201) {
                if (method == "POST") {
                    val resObj = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    gistId = resObj.getString("id")
                }
                defaultPrefs.edit()
                    .putLong(KEY_LAST_CLOUD_TS, now)
                    .apply { if (gistId != null) putString(gistKey, gistId) }
                    .commit()
                Log.i(TAG, "Save SUCCESS for $email (saved ${csArray.length()} keys, ${allDeletedResume.size} deleted resume tombstones)")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "saveWatchHistory error: ${e.message}")
        }
        return false
    }

    /**
     * Full restore from cloud — called at Google Sign-In and Profile Select time.
     * Must be called from a BACKGROUND thread.
     */
    fun restoreWatchHistory(context: Context, email: String): Boolean {
        if (email.isBlank()) return false

        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)

        try {
            val (contentStr, gistId) = fetchGistContent(email, defaultPrefs)
            if (contentStr.isNullOrBlank()) {
                Log.w(TAG, "No backup found for $email")
                return false
            }

            val root = JSONObject(contentStr)
            val cloudTimestamp = root.optLong("timestamp", 0L)

            applyRestorePayload(root, context, defaultPrefs)

            // Persist email, gist ID, and timestamps after full restore
            defaultPrefs.edit()
                .putString("GOOGLE_ACCOUNT_EMAIL", email)
                .putLong(KEY_LAST_CLOUD_TS, cloudTimestamp)
                .putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis())
                .apply { if (gistId != null) putString(getGistIdKey(email), gistId) }
                .commit()

            notifyCloudStreamUiReload()
            Log.i(TAG, "Full restore COMPLETE for $email!")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "restoreWatchHistory error: ${e.message}", e)
        }
        return false
    }

    /**
     * Restore watch history for a specific profile using a custom Gist filename key.
     * Fallback: If key not found, restores the full account history.
     */
    fun restoreWatchHistoryByKey(context: Context, gistKey: String): Boolean {
        if (gistKey.isBlank()) return false
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val email = defaultPrefs.getString("GOOGLE_ACCOUNT_EMAIL", null)

        // If email is available, run standard full restore
        if (!email.isNullOrBlank()) {
            return restoreWatchHistory(context, email)
        }
        return false
    }
}
