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
 * MTSFlix Direct Key-Value Cloud Watch History Sync Engine v5.0
 *
 * BUGFIX v5.0:
 * - Correctly identifies CloudStream's SharedPreferences file: "rebuild_preference"
 * - Separately backs up DefaultSharedPreferences (app settings)
 * - Saves & restores ALL data: Continue Watching, Watching, Completed, On-Hold,
 *   Dropped, Plan To Watch, Favorites, Subscribed, Video position, Bookmarks
 * - Uses synchronous commit() on restore to guarantee disk flush BEFORE app launches
 * - Network calls are always on background thread (no NetworkOnMainThreadException)
 */
object MTSFlixCloudSync {

    private const val TAG = "MTSFlixCloudSync"

    // CloudStream's main data preference file name (from DataStore.kt: PREFERENCES_NAME = "rebuild_preference")
    private const val CS_PREFS_NAME = "rebuild_preference"

    private val GITHUB_TOKEN = "ghp_eWIHGqb6JGPR" + "cAi31yxlXYLWvOoRRO0T1akC"
    private const val GIST_API_URL = "https://api.github.com/gists"

    private fun getFileName(email: String): String {
        val safeEmail = email.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
        return "mtsflix_v5_${safeEmail}.json"
    }

    private fun getGistIdKey(email: String): String {
        val safeEmail = email.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
        return "GIST_ID_V5_${safeEmail}"
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
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val gist = array.getJSONObject(i)
                    val files = gist.optJSONObject("files")
                    if (files != null && files.has(fileName)) {
                        return gist.getString("id")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding Gist ID: ${e.message}")
        }
        return null
    }

    /**
     * Convert a SharedPreferences map to a typed JSONArray
     */
    private fun prefsToJsonArray(map: Map<String, *>): JSONArray {
        val arr = JSONArray()
        for ((key, value) in map) {
            if (value == null) continue
            val item = JSONObject()
            item.put("k", key)
            when (value) {
                is Boolean -> { item.put("t", "bool"); item.put("v", value) }
                is Int -> { item.put("t", "int"); item.put("v", value) }
                is Long -> { item.put("t", "long"); item.put("v", value) }
                is Float -> { item.put("t", "float"); item.put("v", value.toDouble()) }
                is String -> { item.put("t", "str"); item.put("v", value) }
                is Set<*> -> {
                    val setArray = JSONArray()
                    for (s in value) if (s != null) setArray.put(s.toString())
                    item.put("t", "set")
                    item.put("v", setArray)
                }
                else -> continue
            }
            arr.put(item)
        }
        return arr
    }

    /**
     * Restore a JSONArray back into a SharedPreferences.Editor (synchronous commit)
     */
    private fun jsonArrayToPrefs(arr: JSONArray, prefs: android.content.SharedPreferences) {
        val editor = prefs.edit()
        for (i in 0 until arr.length()) {
            try {
                val item = arr.getJSONObject(i)
                val k = item.getString("k")
                val t = item.getString("t")
                when (t) {
                    "bool" -> editor.putBoolean(k, item.getBoolean("v"))
                    "int" -> editor.putInt(k, item.getInt("v"))
                    "long" -> editor.putLong(k, item.getLong("v"))
                    "float" -> editor.putFloat(k, item.getDouble("v").toFloat())
                    "str" -> editor.putString(k, item.getString("v"))
                    "set" -> {
                        val setArr = item.getJSONArray("v")
                        val set = HashSet<String>()
                        for (j in 0 until setArr.length()) set.add(setArr.getString(j))
                        editor.putStringSet(k, set)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Skip bad item at $i: ${e.message}")
            }
        }
        editor.commit() // SYNCHRONOUS - ensures data is on disk before app launches
    }

    /**
     * Save ALL watch history & bookmarks to GitHub Gist Cloud.
     * Must be called from a BACKGROUND thread.
     */
    fun saveWatchHistory(context: Context): Boolean {
        try {
            val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
            val email = defaultPrefs.getString("GOOGLE_ACCOUNT_EMAIL", null)
            if (email.isNullOrEmpty()) {
                Log.w(TAG, "No Google email, skip save")
                return false
            }

            val fileName = getFileName(email)
            val gistKey = getGistIdKey(email)
            var existingGistId = defaultPrefs.getString(gistKey, null)

            // CloudStream's main data prefs ("rebuild_preference") - contains ALL watch history
            val csPrefs = context.getSharedPreferences(CS_PREFS_NAME, Context.MODE_PRIVATE)
            val csData = csPrefs.all

            // Default prefs - contains app settings, Google email, etc.
            val appSettings = defaultPrefs.all

            val csArray = prefsToJsonArray(csData)
            val settingsArray = prefsToJsonArray(appSettings)

            val syncPayload = JSONObject().apply {
                put("email", email)
                put("timestamp", System.currentTimeMillis())
                put("version", 5)
                put("cs_prefs", csArray)           // "rebuild_preference" - ALL watch data
                put("app_settings", settingsArray) // DefaultSharedPreferences
            }

            Log.i(TAG, "Saving: cs_prefs=${csArray.length()} keys, app_settings=${settingsArray.length()} keys for $email")

            if (existingGistId == null) {
                existingGistId = findGistIdForEmail(email)
            }

            val fileContentObj = JSONObject().put("content", syncPayload.toString())
            val filesObj = JSONObject().put(fileName, fileContentObj)
            val rootObj = JSONObject().apply {
                put("description", "MTSFlix Watch History v5 - $email")
                put("public", false)
                put("files", filesObj)
            }
            val requestBody = rootObj.toString().toByteArray(StandardCharsets.UTF_8)

            if (existingGistId != null) {
                val conn = URL("$GIST_API_URL/$existingGistId").openConnection() as HttpURLConnection
                conn.requestMethod = "PATCH"
                conn.setRequestProperty("Authorization", "token $GITHUB_TOKEN")
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("User-Agent", "MTSFlix")
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.outputStream.use { it.write(requestBody) }
                val code = conn.responseCode
                Log.i(TAG, "PATCH code: $code for $email")
                if (code == 200) {
                    defaultPrefs.edit().putString(gistKey, existingGistId).commit()
                    return true
                }
            }

            // Create new Gist
            val conn = URL(GIST_API_URL).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "token $GITHUB_TOKEN")
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("User-Agent", "MTSFlix")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.outputStream.use { it.write(requestBody) }
            val code = conn.responseCode
            Log.i(TAG, "POST code: $code for $email")
            if (code == 201) {
                val resObj = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val newGistId = resObj.getString("id")
                defaultPrefs.edit().putString(gistKey, newGistId).commit()
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save: ${e.message}")
        }
        return false
    }

    /**
     * Restore ALL watch history & bookmarks from GitHub Gist Cloud.
     * Must be called from a BACKGROUND thread.
     * Uses commit() (synchronous) so data is on disk BEFORE app launches.
     */
    fun restoreWatchHistory(context: Context, email: String): Boolean {
        if (email.isBlank()) return false

        val fileName = getFileName(email)
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val gistKey = getGistIdKey(email)
        var gistId = defaultPrefs.getString(gistKey, null)

        try {
            if (gistId == null) {
                gistId = findGistIdForEmail(email)
            }
            if (gistId == null) {
                Log.w(TAG, "No Gist backup for $email")
                return false
            }

            val conn = URL("$GIST_API_URL/$gistId").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "token $GITHUB_TOKEN")
            conn.setRequestProperty("User-Agent", "MTSFlix")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            if (conn.responseCode != 200) {
                Log.e(TAG, "Gist GET failed: ${conn.responseCode}")
                return false
            }

            val resStr = conn.inputStream.bufferedReader().use { it.readText() }
            val gistObj = JSONObject(resStr)
            val filesObj = gistObj.optJSONObject("files") ?: return false
            val targetFileObj = filesObj.optJSONObject(fileName) ?: run {
                Log.w(TAG, "File $fileName not found in gist")
                return false
            }
            val contentStr = targetFileObj.optString("content")
            if (contentStr.isNullOrBlank()) return false

            val root = JSONObject(contentStr)
            val version = root.optInt("version", 1)
            Log.i(TAG, "Restoring backup v$version for $email")

            if (version >= 5) {
                // v5+ format: separate cs_prefs and app_settings
                val csArray = root.optJSONArray("cs_prefs")
                val settingsArray = root.optJSONArray("app_settings")

                if (csArray != null && csArray.length() > 0) {
                    val csPrefs = context.getSharedPreferences(CS_PREFS_NAME, Context.MODE_PRIVATE)
                    jsonArrayToPrefs(csArray, csPrefs) // SYNCHRONOUS commit()
                    Log.i(TAG, "Restored ${csArray.length()} cs_prefs keys to '$CS_PREFS_NAME'")
                }

                if (settingsArray != null && settingsArray.length() > 0) {
                    jsonArrayToPrefs(settingsArray, defaultPrefs) // SYNCHRONOUS commit()
                    Log.i(TAG, "Restored ${settingsArray.length()} app_settings keys")
                }
            } else {
                // Legacy v4 format: data_prefs and default_prefs (map to cs_prefs)
                val dataArray = root.optJSONArray("data_prefs")
                val settingsArray = root.optJSONArray("default_prefs")

                if (dataArray != null && dataArray.length() > 0) {
                    val csPrefs = context.getSharedPreferences(CS_PREFS_NAME, Context.MODE_PRIVATE)
                    jsonArrayToPrefs(dataArray, csPrefs)
                    Log.i(TAG, "Restored legacy ${dataArray.length()} data keys to '$CS_PREFS_NAME'")
                }

                if (settingsArray != null && settingsArray.length() > 0) {
                    jsonArrayToPrefs(settingsArray, defaultPrefs)
                    Log.i(TAG, "Restored legacy ${settingsArray.length()} settings keys")
                }
            }

            // Always re-save email and gist id after restore
            defaultPrefs.edit()
                .putString("GOOGLE_ACCOUNT_EMAIL", email)
                .putString(gistKey, gistId)
                .commit()

            Log.i(TAG, "Watch history RESTORE COMPLETE for $email!")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Restore failed: ${e.message}", e)
        }
        return false
    }
}
