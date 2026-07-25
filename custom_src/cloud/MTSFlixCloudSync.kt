package com.mts.mtsflix.cloud

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.BackupUtils
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.DataStore.getSharedPrefs
import com.lagradost.cloudstream3.utils.DataStore.getDefaultSharedPrefs
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.PrintWriter

/**
 * MTSFlix Cloud & Local Storage Watch History Sync Engine
 *
 * Automatically backs up and restores watch history (movies, series, continue watching, bookmarks)
 * linked to the user's Google Account email across clear data / app re-installs.
 */
object MTSFlixCloudSync {

    private const val TAG = "MTSFlixCloudSync"
    private const val BACKUP_DIR_NAME = "MTSFlix_Backups"

    /**
     * Get the persistent public backup directory (outside app internal data so it survives Clear Data / Uninstall)
     */
    private fun getPublicBackupFile(email: String): File {
        val safeEmail = email.replace(Regex("[^a-zA-Z0-9_.]"), "_")
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val mtsDir = File(downloadsDir, BACKUP_DIR_NAME)
        if (!mtsDir.exists()) {
            mtsDir.mkdirs()
        }
        return File(mtsDir, "MTSFlix_History_${safeEmail}.json")
    }

    /**
     * Backup watch history, resume watching data, and settings to persistent storage
     */
    fun saveWatchHistory(context: Context): Boolean {
        return try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val email = prefs.getString("GOOGLE_ACCOUNT_EMAIL", null) ?: "default_user"
            val targetFile = getPublicBackupFile(email)

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

            FileOutputStream(targetFile).use { fos ->
                PrintWriter(fos).use { pw ->
                    pw.print(jsonString)
                }
            }

            Log.i(TAG, "Watch history successfully backed up for $email to ${targetFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save watch history: ${e.message}")
            false
        }
    }

    /**
     * Restore watch history when user signs in with Google (survives Clear Data & Uninstall)
     */
    fun restoreWatchHistory(context: Context, email: String): Boolean {
        return try {
            val targetFile = getPublicBackupFile(email)
            if (!targetFile.exists() || targetFile.length() == 0L) {
                Log.w(TAG, "No previous cloud/persistent backup found for $email at ${targetFile.absolutePath}")
                return false
            }

            val jsonString = FileInputStream(targetFile).bufferedReader().use { it.readText() }
            if (jsonString.isBlank()) return false

            val backupFile = parseJson<BackupUtils.BackupFile>(jsonString)
            BackupUtils.restore(context, backupFile, restoreSettings = true, restoreDataStore = true)

            // Re-save Google email into freshly restored prefs
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putString("GOOGLE_ACCOUNT_EMAIL", email).apply()

            Log.i(TAG, "Watch history successfully RESTORED for $email!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore watch history: ${e.message}")
            false
        }
    }
}
