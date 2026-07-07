package com.mts.mtsflix

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MTSFlix Logger
 * Manages system logging for License, Auth, Extensions, Providers, Extraction, and Playback.
 * Stores logs locally in mtsflix_debug.log.
 */
object MTSFlixLogger {
    private const val TAG_PREFIX = "MTSFlix:"
    private const val LOG_FILE_NAME = "mtsflix_debug.log"
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        try {
            logFile = File(context.filesDir, LOG_FILE_NAME)
            if (!logFile!!.exists()) {
                logFile!!.createNewFile()
            }
            log("SYSTEM", "Logger initialized. File path: ${logFile!!.absolutePath}")
        } catch (e: Exception) {
            Log.e("MTSFlix:Logger", "Failed to initialize log file: ${e.message}")
        }
    }

    @Synchronized
    fun log(category: String, message: String) {
        val tag = "$TAG_PREFIX$category"
        // Also print to Logcat for real-time debug console monitoring
        Log.i(tag, message)
        
        try {
            logFile?.let { file ->
                val timestamp = dateFormat.format(Date())
                val logLine = "[$timestamp] [$category] $message\n"
                file.appendText(logLine)
            }
        } catch (e: Exception) {
            Log.e("MTSFlix:Logger", "Failed to write log line: ${e.message}")
        }
    }

    fun getLogContent(): String {
        return try {
            logFile?.readText() ?: "Log file not initialized"
        } catch (e: Exception) {
            "Error reading log file: ${e.message}"
        }
    }

    fun clearLog() {
        try {
            logFile?.writeText("")
            log("SYSTEM", "Log cleared")
        } catch (e: Exception) {
            Log.e("MTSFlix:Logger", "Failed to clear log file: ${e.message}")
        }
    }
}
