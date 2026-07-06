package com.mts.mtsflix.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * MTSFlix In-App Auto Update Manager
 *
 * Checks version.json on GitHub and downloads + installs newer APK.
 * Does NOT require uninstalling — works because same signing keystore.
 *
 * Flow:
 * 1. Fetch version.json from GitHub
 * 2. Compare versionCode with current app
 * 3. Show update dialog (mandatory or optional)
 * 4. Download APK via DownloadManager
 * 5. Trigger install via PackageInstaller
 *
 * Requirements in AndroidManifest.xml (added by apply.sh):
 * - android.permission.REQUEST_INSTALL_PACKAGES
 * - android.permission.INTERNET
 * - FileProvider configuration
 */
object AutoUpdateManager {

    private const val TAG = "MTSFlix:Update"
    private const val VERSION_JSON_URL =
        "https://raw.githubusercontent.com/muhamadakmal854-svg/MTSFlix/main/version.json"

    private var activeDownloadId: Long = -1
    private var downloadReceiver: BroadcastReceiver? = null

    // ─── Update Info ─────────────────────────────────────────────────────────

    data class UpdateInfo(
        val version: String,
        val versionCode: Int,
        val downloadUrl: String,
        val releaseNotes: String,
        val mandatory: Boolean,
        val sha256: String,
        val publishedAt: String
    )

    // ─── Check & Prompt ───────────────────────────────────────────────────────

    /**
     * Call from MainActivity.onResume() or after license check.
     * Silently checks for updates and shows dialog if available.
     */
    fun checkAndPrompt(context: Context, lifecycleScope: LifecycleCoroutineScope) {
        lifecycleScope.launch {
            try {
                val update = checkForUpdate(context) ?: return@launch
                Log.i(TAG, "Update available: v${update.version}")
                showUpdateDialog(context, update)
            } catch (e: Exception) {
                Log.d(TAG, "Update check skipped: ${e.message}")
            }
        }
    }

    /**
     * Returns UpdateInfo if a newer version is available, null otherwise.
     */
    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val json = fetchVersionJson()
            val obj = JSONObject(json)

            val remoteVersionCode = obj.optInt("versionCode", 0)
            val currentVersionCode = getCurrentVersionCode(context)

            Log.d(TAG, "Current: $currentVersionCode, Remote: $remoteVersionCode")

            if (remoteVersionCode > currentVersionCode) {
                UpdateInfo(
                    version = obj.optString("version", "?"),
                    versionCode = remoteVersionCode,
                    downloadUrl = obj.optString("downloadUrl", ""),
                    releaseNotes = obj.optString("releaseNotes", "Update tersedia"),
                    mandatory = obj.optBoolean("mandatory", false),
                    sha256 = obj.optString("sha256", ""),
                    publishedAt = obj.optString("publishedAt", "")
                )
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed: ${e.message}")
            null
        }
    }

    // ─── Update Dialog ────────────────────────────────────────────────────────

    fun showUpdateDialog(context: Context, info: UpdateInfo) {
        if (context !is android.app.Activity) return
        if (context.isFinishing || context.isDestroyed) return

        context.runOnUiThread {
            AlertDialog.Builder(context)
                .setTitle("🎬 Update MTSFlix v${info.version}")
                .setMessage(
                    buildString {
                        appendLine(info.releaseNotes)
                        appendLine()
                        appendLine("📥 Download dan install update sekarang?")
                        appendLine()
                        appendLine("ℹ️ Data anda tidak akan hilang.")
                        if (info.mandatory) appendLine("\n⚠️ Update ini WAJIB untuk teruskan penggunaan.")
                    }
                )
                .setPositiveButton("Update Sekarang") { _, _ ->
                    if (info.downloadUrl.isNotBlank()) {
                        downloadAndInstall(context, info)
                    }
                }
                .apply {
                    if (!info.mandatory) setNegativeButton("Kemudian", null)
                }
                .setCancelable(!info.mandatory)
                .show()
        }
    }

    // ─── Download & Install ───────────────────────────────────────────────────

    fun downloadAndInstall(context: Context, info: UpdateInfo) {
        Log.i(TAG, "Starting download: ${info.downloadUrl}")

        val fileName = "MTSFlix-v${info.version}.apk"

        // Check & request install permission (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                requestInstallPermission(context)
                return
            }
        }

        // Remove old downloaded file if exists
        val oldFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
        if (oldFile.exists()) oldFile.delete()

        val request = DownloadManager.Request(
            Uri.parse(info.downloadUrl)
        ).apply {
            setTitle("MTSFlix Update v${info.version}")
            setDescription("Memuat turun... Sila tunggu.")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setMimeType("application/vnd.android.package-archive")
            setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
            )
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        activeDownloadId = dm.enqueue(request)

        Log.i(TAG, "Download enqueued: ID=$activeDownloadId")

        // Register completion receiver
        downloadReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }

        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == activeDownloadId) {
                    try { ctx.unregisterReceiver(this) } catch (_: Exception) {}
                    downloadReceiver = null
                    onDownloadComplete(ctx, dm, id, fileName)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
    }

    private fun onDownloadComplete(context: Context, dm: DownloadManager, downloadId: Long, fileName: String) {
        // Check if download was successful
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = dm.query(query)
        var success = false

        if (cursor.moveToFirst()) {
            val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val status = cursor.getInt(statusCol)
            success = (status == DownloadManager.STATUS_SUCCESSFUL)
        }
        cursor.close()

        if (!success) {
            Log.e(TAG, "Download failed!")
            return
        }

        Log.i(TAG, "Download complete, triggering install...")
        installApk(context, fileName)
    }

    private fun installApk(context: Context, fileName: String) {
        val apkFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )

        if (!apkFile.exists()) {
            Log.e(TAG, "APK file not found: ${apkFile.absolutePath}")
            return
        }

        val apkUri = try {
            // Android 7+: Use FileProvider
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
        } catch (e: Exception) {
            // Fallback for older Android
            Uri.fromFile(apkFile)
        }

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        try {
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Install intent failed: ${e.message}")
        }
    }

    private fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(intent)
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun fetchVersionJson(): String {
        val url = URL(VERSION_JSON_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Cache-Control", "no-cache, no-store")
            setRequestProperty("User-Agent", "MTSFlix-UpdateChecker/1.0")
        }
        return try {
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().readText()
            else throw Exception("HTTP ${conn.responseCode}")
        } finally {
            conn.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun getCurrentVersionCode(context: Context): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, 0)
                    .longVersionCode.toInt()
            } else {
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode
            }
        } catch (e: Exception) { 0 }
    }
}
