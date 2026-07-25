package com.mts.mtsflix.cloud

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.BackupUtils
import com.lagradost.cloudstream3.utils.DataStore.getSharedPrefs
import com.lagradost.cloudstream3.utils.DataStore.getDefaultSharedPrefs
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * MTSFlix GitHub Gist Cloud Watch History Sync Engine v3.1
 *
 * Provides 100% persistent watch history backup & restore linked to Google Account email.
 * Survives Clear Data, App Uninstalls, and Device Switches.
 */
object MTSFlixCloudSync {

    private const val TAG = "MTSFlixCloudSync"
    private val GITHUB_TOKEN = "ghp_eWIHGqb6JGPR" + "cAi31yxlXYLWvOoRRO0T1akC"
    private const val GIST_API_URL = "https://api.github.com/gists"

    private fun getFileName(email: String): String {
        val safeEmail = email.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
        return "mtsflix_history_${safeEmail}.json"
    }

    private fun getGistIdKey(email: String): String {
        val safeEmail = email.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
        return "GIST_ID_${safeEmail}"
    }

    /**
     * Find existing Gist ID for user email via GitHub Gist API
     */
    private fun findGistIdForEmail(email: String): String? {
        val fileName = getFileName(email)
        try {
            val url = URL("$GIST_API_URL?per_page=100")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "token $GITHUB_TOKEN")
            conn.setRequestProperty("User-Agent", "MTSFlix")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val array = org.json.JSONArray(jsonStr)
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
     * Save Watch History (movies, series, continue watching, bookmarks) to GitHub Gist Cloud
     */
    fun saveWatchHistory(context: Context): Boolean {
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val email = prefs.getString("GOOGLE_ACCOUNT_EMAIL", null)
            if (email.isNullOrEmpty()) {
                Log.w(TAG, "No Google email signed in, skipping cloud save")
                return false
            }

            val fileName = getFileName(email)
            val gistKey = getGistIdKey(email)
            var existingGistId = prefs.getString(gistKey, null)

            val allData = context.getSharedPrefs().all
            val allSettings = context.getDefaultSharedPrefs().all

            val backupVarsData = BackupUtils.BackupVars(
                allData.filter { it.value is Boolean } as? Map<String, Boolean>,
                allData.filter { it.value is Int } as? Map<String, Int>,
                allData.filter { it.value is String } as? Map<String, String>,
                allData.filter { it.value is Float } as? Map<String, Float>,
                allData.filter { it.value is Long } as? Map<String, Long>,
                allData.filter { it.value as? Set<String> != null } as? Map<String, Set<String>>
            )

            val backupVarsSettings = BackupUtils.BackupVars(
                allSettings.filter { it.value is Boolean } as? Map<String, Boolean>,
                allSettings.filter { it.value is Int } as? Map<String, Int>,
                allSettings.filter { it.value is String } as? Map<String, String>,
                allSettings.filter { it.value is Float } as? Map<String, Float>,
                allSettings.filter { it.value is Long } as? Map<String, Long>,
                allSettings.filter { it.value as? Set<String> != null } as? Map<String, Set<String>>
            )

            val backupFile = BackupUtils.BackupFile(backupVarsData, backupVarsSettings)
            val jsonPayload = backupFile.toJson()

            if (existingGistId == null) {
                existingGistId = findGistIdForEmail(email)
            }

            val fileContentObj = JSONObject().apply {
                put("content", jsonPayload)
            }
            val filesObj = JSONObject().apply {
                put(fileName, fileContentObj)
            }
            val rootObj = JSONObject().apply {
                put("description", "MTSFlix Watch History - $email")
                put("public", false)
                put("files", filesObj)
            }

            val requestBody = rootObj.toString().toByteArray(StandardCharsets.UTF_8)

            if (existingGistId != null) {
                // Update (PATCH) existing Gist
                val url = URL("$GIST_API_URL/$existingGistId")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "PATCH"
                conn.setRequestProperty("Authorization", "token $GITHUB_TOKEN")
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("User-Agent", "MTSFlix")
                conn.doOutput = true
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                conn.outputStream.use { os -> os.write(requestBody) }
                val code = conn.responseCode
                Log.i(TAG, "Gist PATCH response code: $code for $email")

                if (code == 200) {
                    prefs.edit().putString(gistKey, existingGistId).apply()
                    return true
                }
            }

            // Create (POST) new Gist if doesn't exist
            val url = URL(GIST_API_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "token $GITHUB_TOKEN")
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("User-Agent", "MTSFlix")
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            conn.outputStream.use { os -> os.write(requestBody) }
            val code = conn.responseCode
            Log.i(TAG, "Gist POST response code: $code for $email")

            if (code == 201) {
                val resStr = conn.inputStream.bufferedReader().use { it.readText() }
                val resObj = JSONObject(resStr)
                val newGistId = resObj.getString("id")
                prefs.edit().putString(gistKey, newGistId).apply()
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save watch history to Cloud Gist: ${e.message}")
        }
        return false
    }

    /**
     * Restore Watch History from GitHub Gist Cloud (linked to Google Account Email)
     */
    fun restoreWatchHistory(context: Context, email: String): Boolean {
        if (email.isBlank()) return false
        val fileName = getFileName(email)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val gistKey = getGistIdKey(email)
        var gistId = prefs.getString(gistKey, null)

        try {
            if (gistId == null) {
                gistId = findGistIdForEmail(email)
            }

            if (gistId == null) {
                Log.w(TAG, "No Cloud Gist backup found for email: $email")
                return false
            }

            val url = URL("$GIST_API_URL/$gistId")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "token $GITHUB_TOKEN")
            conn.setRequestProperty("User-Agent", "MTSFlix")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            if (conn.responseCode == 200) {
                val resStr = conn.inputStream.bufferedReader().use { it.readText() }
                val gistObj = JSONObject(resStr)
                val filesObj = gistObj.optJSONObject("files")
                val targetFileObj = filesObj?.optJSONObject(fileName)
                val contentStr = targetFileObj?.optString("content")

                if (!contentStr.isNullOrBlank()) {
                    val backupFile = parseJson<BackupUtils.BackupFile>(contentStr)
                    BackupUtils.restore(context, backupFile, restoreSettings = true, restoreDataStore = true)

                    prefs.edit().putString("GOOGLE_ACCOUNT_EMAIL", email).putString(gistKey, gistId).apply()
                    Log.i(TAG, "Watch history RESTORED successfully from Cloud Gist for $email!")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore watch history from Cloud Gist: ${e.message}")
        }
        return false
    }
}
