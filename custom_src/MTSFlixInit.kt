package com.mts.mtsflix

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager

/**
 * MTSFlix App Initializer
 * Call MTSFlixInit.initialize(context) from LicenseCheckActivity
 */
object MTSFlixInit {

    private const val TAG = "MTSFlix:Init"
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true

        // Initialize file logging first
        MTSFlixLogger.init(context)

        MTSFlixLogger.log("SYSTEM", "=======================================")
        MTSFlixLogger.log("SYSTEM", "   MTSFlix Initializing...")
        MTSFlixLogger.log("SYSTEM", "=======================================")

        // Mark CloudStream setup as complete so MainActivity goes straight to Home
        try {
            markSetupComplete(context)
            MTSFlixLogger.log("SYSTEM", "✅ CloudStream setup marked as complete")
        } catch (e: Exception) {
            MTSFlixLogger.log("SYSTEM", "Setup mark error: ${e.message}")
        }

        MTSFlixLogger.log("SYSTEM", "✅ MTSFlix initialized successfully")
    }

    private fun markSetupComplete(context: Context) {
        val key = "HAS_DONE_SETUP"
        try {
            context.getSharedPreferences("rebuild_preference", Context.MODE_PRIVATE)
                .edit().putBoolean(key, true).apply()
        } catch (e: Exception) {
            Log.w("MTSFlix", "markSetupComplete rebuild_preference: ${e.message}")
        }
        try {
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putBoolean(key, true).apply()
        } catch (e: Exception) {
            Log.w("MTSFlix", "markSetupComplete defaultPrefs: ${e.message}")
        }
    }

    fun isInitialized() = initialized
}
