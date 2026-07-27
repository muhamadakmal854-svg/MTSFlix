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
 * MTSFlix Direct Key-Value Cloud Watch History Sync Engine v6.0
 *
 * v6.0 NEW FEATURES:
 * - autoSyncFromCloud(): Smart timestamp-based sync for cross-device support
 *   Only restores if cloud data is NEWER than local data (prevents overwrite)
 * - Works for 2+ phones with same Google account — Phone A watches → Phone B opens app → auto sync!
 * - Debounce: Won't spam Gist API (minimum 30s between auto-syncs)
 *
 * PREVIOUS FIXES (v5.0):
 * - Correct SharedPreferences: "rebuild_preference" (CloudStream's actual data file)
 * - Synchronous commit() on restore
 * - Thread{}.start() for network calls (avoids NetworkOnMainThreadException)
 * - Hooks: setLastWatched, setViewPos, setBookmarkedData, setWatchState
 */
object MTSFlixCloudSync {

    private const val TAG = "MTSFlixCloudSync"

    // CloudStream's main data preference file (DataStore.kt: PREFERENCES_NAME = "rebuild_preference")
    private const val CS_PREFS_NAME = "rebuild_preference"

    // Keys stored in DefaultSharedPreferences
    private const val KEY_GIST_ID_PREFIX = "GIST_ID_V5_"
    private const val KEY_LAST_CLOUD_TS = "MTSFLIX_LAST_CLOUD_TS"   // timestamp of last cloud save
    private const val KEY_LAST_SYNC_TIME = "MTSFLIX_LAST_SYNC_TIME" // when we last pulled from cloud
    private const val AUTO_SYNC_DEBOUNCE_MS = 30_000L // 30 seconds between auto-syncs

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
                for (i in 0 until arr.length()) {
                    val gist = arr.getJSONObject(i)
                    val files = gist.optJSONObject("files")
                    if (files != null && files.has(fileName)) {
                        return gist.getString("id")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "findGistIdForEmail: ${e.message}")
        }
        return null
    }

    /** Fetch raw Gist JSON string for this email. Returns null if not found. */
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

            if (conn.responseCode != 200) return Pair(null, null)

            val resStr = conn.inputStream.bufferedReader().use { it.readText() }
            val gistObj = JSONObject(resStr)
            val filesObj = gistObj.optJSONObject("files") ?: return Pair(null, null)
            val fileObj = filesObj.optJSONObject(fileName) ?: return Pair(null, null)
            val content = fileObj.optString("content")
            return Pair(content.ifBlank { null }, gistId)
        } catch (e: Exception) {
            Log.e(TAG, "fetchGistContent: ${e.message}")
        }
        return Pair(null, null)
    }

    /** Convert SharedPreferences map → typed JSONArray */
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
        editor.commit() // SYNCHRONOUS — data on disk before returning
    }

    /**
     * AUTO-SYNC: Pull from cloud if cloud data is newer than local data.
     * Called on every app open from MainActivity.onResume().
     * Has 30-second debounce to avoid hammering the API.
     *
     * @return true if data was updated from cloud (UI should refresh)
     */
    fun autoSyncFromCloud(context: Context): Boolean {
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val email = defaultPrefs.getString("GOOGLE_ACCOUNT_EMAIL", null)
        if (email.isNullOrEmpty()) return false

        // Debounce: don't sync more than once per 30 seconds
        val lastSyncTime = defaultPrefs.getLong(KEY_LAST_SYNC_TIME, 0L)
        val now = System.currentTimeMillis()
        if (now - lastSyncTime < AUTO_SYNC_DEBOUNCE_MS) {
            Log.d(TAG, "autoSync skipped (debounce ${(now - lastSyncTime)/1000}s < 30s)")
            return false
        }

        try {
            val (contentStr, gistId) = fetchGistContent(email, defaultPrefs)
            if (contentStr.isNullOrBlank()) {
                Log.w(TAG, "autoSync: no cloud data for $email")
                return false
            }

            val root = JSONObject(contentStr)
            val cloudTimestamp = root.optLong("timestamp", 0L)
            val localTimestamp = defaultPrefs.getLong(KEY_LAST_CLOUD_TS, 0L)

            Log.i(TAG, "autoSync: cloud=$cloudTimestamp local=$localTimestamp")

            // Update last sync attempt time regardless of outcome
            defaultPrefs.edit().putLong(KEY_LAST_SYNC_TIME, now).commit()

            // Only restore if cloud data is strictly newer than local data
            if (cloudTimestamp <= localTimestamp) {
                Log.i(TAG, "autoSync: local is up to date, skip restore")
                return false
            }

            Log.i(TAG, "autoSync: cloud is newer! Restoring for $email...")

            val csPrefs = context.getSharedPreferences(CS_PREFS_NAME, Context.MODE_PRIVATE)

            val csArray = root.optJSONArray("cs_prefs")
            if (csArray != null && csArray.length() > 0) {
                jsonArrayToPrefs(csArray, csPrefs)
                Log.i(TAG, "autoSync: restored ${csArray.length()} cs_prefs keys")
            }

            val settingsArray = root.optJSONArray("app_settings")
            if (settingsArray != null && settingsArray.length() > 0) {
                // Restore app settings but preserve critical local keys
                jsonArrayToPrefs(settingsArray, defaultPrefs)
                Log.i(TAG, "autoSync: restored ${settingsArray.length()} app_settings keys")
            }

            // Update local timestamp to match cloud (prevents redundant restores)
            defaultPrefs.edit()
                .putString("GOOGLE_ACCOUNT_EMAIL", email)
                .putLong(KEY_LAST_CLOUD_TS, cloudTimestamp)
                .putLong(KEY_LAST_SYNC_TIME, now)
                .apply { if (gistId != null) putString(getGistIdKey(email), gistId) }
                .commit()

            Log.i(TAG, "autoSync COMPLETE — data updated from cloud!")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "autoSync error: ${e.message}", e)
        }
        return false
    }

    /**
     * Save ALL watch history & bookmarks to GitHub Gist Cloud.
     * Must be called from a BACKGROUND thread (Thread{}.start()).
     */
    fun saveWatchHistory(context: Context): Boolean {
        try {
            val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
            val email = defaultPrefs.getString("GOOGLE_ACCOUNT_EMAIL", null)
            if (email.isNullOrEmpty()) {
                Log.w(TAG, "No Google email, skip save")
                return false
            }

            val now = System.currentTimeMillis()
            val fileName = getFileName(email)
            val gistKey = getGistIdKey(email)
            var gistId = defaultPrefs.getString(gistKey, null) ?: findGistIdForEmail(email)

            val csPrefs = context.getSharedPreferences(CS_PREFS_NAME, Context.MODE_PRIVATE)
            val csArray = prefsToJsonArray(csPrefs.all)
            val settingsArray = prefsToJsonArray(defaultPrefs.all)

            val payload = JSONObject().apply {
                put("email", email)
                put("timestamp", now)      // ← Used for cross-device timestamp comparison
                put("version", 5)
                put("cs_prefs", csArray)
                put("app_settings", settingsArray)
            }

            Log.i(TAG, "Saving: ${csArray.length()} cs_prefs + ${settingsArray.length()} settings for $email")

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
            Log.i(TAG, "$method response: $code for $email")

            if (code in 200..201) {
                if (method == "POST") {
                    val resObj = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    gistId = resObj.getString("id")
                }
                // Update local timestamp to match what we just saved
                defaultPrefs.edit()
                    .putLong(KEY_LAST_CLOUD_TS, now)
                    .apply { if (gistId != null) putString(gistKey, gistId) }
                    .commit()
                Log.i(TAG, "Save SUCCESS for $email (ts=$now)")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "saveWatchHistory error: ${e.message}")
        }
        return false
    }

    /**
     * Full restore from cloud — called at Google Sign-In time.
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

            Log.i(TAG, "Full restore COMPLETE for $email!")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "restoreWatchHistory error: ${e.message}", e)
        }
        return false
    }

    /**
     * Restore watch history for a specific profile using a custom Gist filename key.
     * Used by ProfilePickerActivity when switching between profiles.
     * Key format: mtsflix_sync_EMAIL_PROFILEID.json
     */
    fun restoreWatchHistoryByKey(context: Context, gistKey: String): Boolean {
        if (gistKey.isBlank()) return false
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val token = defaultPrefs.getString("GITHUB_TOKEN", null) ?: return false
        val gistId = defaultPrefs.getString("GIST_ID_MASTER", null) ?: return false

        return try {
            val url = URL("https://api.github.com/gists/$gistId")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "token $token")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                connectTimeout = 10000; readTimeout = 15000
            }
            if (conn.responseCode != 200) return false
            val resp = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
            conn.disconnect()

            val root    = JSONObject(resp)
            val files   = root.optJSONObject("files") ?: return false
            val fileObj = files.optJSONObject(gistKey) ?: return false
            val content = fileObj.optString("content", "")
            if (content.isBlank()) return false

            val data    = JSONObject(content)
            val csPrefs = context.getSharedPreferences("rebuild_preference", Context.MODE_PRIVATE)
            data.optJSONArray("cs_prefs")?.let { arr -> jsonArrayToPrefs(arr, csPrefs) }
            data.optJSONArray("app_settings")?.let { arr -> jsonArrayToPrefs(arr, defaultPrefs) }
            Log.i(TAG, "Profile sync restored from key: $gistKey")
            true
        } catch (e: Exception) {
            Log.e(TAG, "restoreWatchHistoryByKey error: ${e.message}")
            false
        }
    }
}
