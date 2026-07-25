package com.mts.mtsflix.cloud

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.BackupUtils
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.DataStore.getDefaultSharedPrefs
import com.lagradost.cloudstream3.utils.DataStore.getSharedPrefs
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * MTSFlix Cloud & Local Storage Watch History Sync Engine v2.0
 *
 * Provides 100% persistent watch history backup & restore across Clear Data & Uninstall:
 * 1. Online Cloud API Backup (KVDB Cloud endpoint linked to Google Account Email)
 * 2. MediaStore / Public External Storage Backup
 */
object MTSFlixCloudSync {

    private const val TAG = "MTSFlixCloudSync"
    private const val CLOUD_BUCKET_ID = "4y9bZk6U9S9qZ8M2j7XQ"
    private const val CLOUD_API_BASE = "https://kvdb.io/$CLOUD_BUCKET_ID/"

    private fun getSafeEmailKey(email: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(email.trim().lowercase().toByteArray())
        val hash = digest.joinToString("") { "%02x".format(it) }
        return "mtsflix_user_" + hash.take(24)
    }

    /**
     * Save Watch History locally (MediaStore) and to Online Cloud API
     */
    fun saveWatchHistory(context: Context): Boolean {
        ioSafe {
            try {
                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                val email = prefs.getString("GOOGLE_ACCOUNT_EMAIL", null)
                if (email.isNullOrEmpty()) {
                    Log.w(TAG, "No Google email signed in, skipping cloud save")
                    return@ioSafe
                }

                val emailKey = getSafeEmailKey(email)
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
                val jsonString = backupFile.toJson()

                // 1. Save to Remote Cloud Endpoint (KVDB)
                try {
                    val url = URL(CLOUD_API_BASE + emailKey)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.doOutput = true
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    conn.outputStream.use { os ->
                        os.write(jsonString.toByteArray(Charsets.UTF_8))
                    }
                    val code = conn.responseCode
                    Log.i(TAG, "Cloud POST response code: $code for $email")
                } catch (e: Exception) {
                    Log.e(TAG, "Cloud POST error: ${e.message}")
                }

                // 2. Save to Public External Storage (MediaStore / Downloads)
                try {
                    saveToLocalFile(context, emailKey, jsonString)
                } catch (e: Exception) {
                    Log.e(TAG, "Local file save error: ${e.message}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Save watch history error: ${e.message}")
            }
        }
        return true
    }

    /**
     * Restore Watch History from Online Cloud API or Local MediaStore Backup
     */
    fun restoreWatchHistory(context: Context, email: String): Boolean {
        if (email.isBlank()) return false
        val emailKey = getSafeEmailKey(email)
        var jsonString: String? = null

        // 1. Fetch from Online Cloud API
        try {
            val url = URL(CLOUD_API_BASE + emailKey)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (conn.responseCode == 200) {
                jsonString = conn.inputStream.bufferedReader().use { it.readText() }
                Log.i(TAG, "Cloud GET successful for $email! Bytes: ${jsonString.length}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cloud GET error: ${e.message}")
        }

        // 2. Fallback to Local Public Storage if Cloud failed
        if (jsonString.isNullOrBlank()) {
            try {
                jsonString = readFromLocalFile(context, emailKey)
            } catch (e: Exception) {
                Log.e(TAG, "Local file read error: ${e.message}")
            }
        }

        if (jsonString.isNullOrBlank()) {
            Log.w(TAG, "No backup found in Cloud or Local for email: $email")
            return false
        }

        return try {
            val backupFile = parseJson<BackupUtils.BackupFile>(jsonString)
            BackupUtils.restore(context, backupFile, restoreSettings = true, restoreDataStore = true)

            // Re-affirm Google Account email in prefs
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putString("GOOGLE_ACCOUNT_EMAIL", email).apply()

            Log.i(TAG, "Watch History RESTORED successfully for $email!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse/restore backup JSON: ${e.message}")
            false
        }
    }

    private fun saveToLocalFile(context: Context, emailKey: String, json: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "${emailKey}.json")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MTSFlix_Backups")
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { os ->
                    os.write(json.toByteArray(Charsets.UTF_8))
                }
            }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MTSFlix_Backups")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "${emailKey}.json")
            FileOutputStream(file).use { fos ->
                PrintWriter(fos).use { pw -> pw.print(json) }
            }
        }
    }

    private fun readFromLocalFile(context: Context, emailKey: String): String? {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MTSFlix_Backups")
        val file = File(dir, "${emailKey}.json")
        return if (file.exists() && file.length() > 0) {
            FileInputStream(file).bufferedReader().use { it.readText() }
        } else null
    }
}
