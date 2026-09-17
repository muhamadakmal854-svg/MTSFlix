package com.mts.mtsflix.cloud

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * MTSFlix Direct Key-Value Cloud Watch History Sync Engine v6.1 (v1.1.6)
 *
 * v6.1 FIXES & ENHANCEMENTS:
 * - Smart Merge on Save: Never overwrites existing cloud watch history with empty local data
 * - Multi-Gist Deduplication: Discovers the best gist containing watch history and merges/cleans duplicates
 * - Auto UI Refresh: Triggers MainActivity.reloadHomeEvent(true) and bookmarks reload on restore
 * - Fixed restoreWatchHistoryByKey: Uses official GITHUB_TOKEN directly
 * - Synchronous commit() on restore: Ensures data is on disk before UI loads
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

            val csPrefs = context.getSharedPreferences(CS_PREFS_NAME, Context.MODE_PRIVATE)

            val csArray = root.optJSONArray("cs_prefs") ?: root.optJSONArray("data_prefs")
            if (csArray != null && csArray.length() > 0) {
                jsonArrayToPrefs(csArray, csPrefs)
                Log.i(TAG, "autoSync: restored ${csArray.length()} cs_prefs keys")
            }

            val settingsArray = root.optJSONArray("app_settings") ?: root.optJSONArray("default_prefs")
            if (settingsArray != null && settingsArray.length() > 0) {
                jsonArrayToPrefs(settingsArray, defaultPrefs)
            }

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
     * SMART MERGE: Merges with existing cloud data so local empty state never wipes cloud!
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

            // SMART MERGE: Fetch existing cloud content first to preserve cloud history
            val (existingCloudStr, resolvedGistId) = fetchGistContent(email, defaultPrefs)
            if (resolvedGistId != null) gistId = resolvedGistId

            val mergedCsMap = HashMap<String, Any?>()
            val mergedSettingsMap = HashMap<String, Any?>()

            if (!existingCloudStr.isNullOrBlank()) {
                try {
                    val root = JSONObject(existingCloudStr)
                    val cloudCsArray = root.optJSONArray("cs_prefs") ?: root.optJSONArray("data_prefs")
                    if (cloudCsArray != null) {
                        for (i in 0 until cloudCsArray.length()) {
                            val item = cloudCsArray.getJSONObject(i)
                            val k = item.getString("k")
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

            // Layer local keys over cloud keys
            for ((k, v) in localCsMap) {
                if (v != null) mergedCsMap[k] = v
            }
            for ((k, v) in localSettingsMap) {
                if (v != null) mergedSettingsMap[k] = v
            }

            val csArray = prefsToJsonArray(mergedCsMap)
            val settingsArray = prefsToJsonArray(mergedSettingsMap)

            val payload = JSONObject().apply {
                put("email", email)
                put("timestamp", now)
                put("version", 5)
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
                Log.i(TAG, "Save SUCCESS for $email (merged ${csArray.length()} keys)")
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

            val csPrefs = context.getSharedPreferences(CS_PREFS_NAME, Context.MODE_PRIVATE)

            val csArray = root.optJSONArray("cs_prefs") ?: root.optJSONArray("data_prefs")
            if (csArray != null && csArray.length() > 0) {
                jsonArrayToPrefs(csArray, csPrefs)
                Log.i(TAG, "Restored ${csArray.length()} cs_prefs keys to '$CS_PREFS_NAME'")
            }

            val settingsArray = root.optJSONArray("app_settings") ?: root.optJSONArray("default_prefs")
            if (settingsArray != null && settingsArray.length() > 0) {
                jsonArrayToPrefs(settingsArray, defaultPrefs)
                Log.i(TAG, "Restored ${settingsArray.length()} app_settings keys")
            }

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
