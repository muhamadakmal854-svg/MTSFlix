package com.mts.mtsflix

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.File
import java.net.URL

/**
 * MTSFlix Video Provider Engine
 * Intercepts, auto-detects, and records new media stream formats and player pattern hosts.
 * Saves detected extraction patterns to extracted_patterns.json.
 */
object VideoProviderEngine {
    private const val FILE_NAME = "extracted_patterns.json"
    private var patternFile: File? = null
    private val patterns = mutableListOf<String>()

    fun init(context: Context) {
        try {
            patternFile = File(context.filesDir, FILE_NAME)
            if (!patternFile!!.exists()) {
                patternFile!!.createNewFile()
                patternFile!!.writeText("[]")
            }
            loadPatterns()
            MTSFlixLogger.log("PROVIDER", "VideoProviderEngine initialized. Total patterns: ${patterns.size}")
        } catch (e: Exception) {
            MTSFlixLogger.log("PROVIDER", "Failed to init provider engine: ${e.message}")
        }
    }

    private fun loadPatterns() {
        try {
            val content = patternFile?.readText() ?: "[]"
            val array = JSONArray(content)
            patterns.clear()
            for (i in 0 until array.length()) {
                patterns.add(array.getString(i))
            }
        } catch (e: Exception) {
            Log.e("MTSFlix:Provider", "Error loading patterns: ${e.message}")
        }
    }

    @Synchronized
    fun detectAndRecordPattern(url: String) {
        try {
            val host = URL(url).host
            if (host.isBlank()) return
            
            // Check if host is a new player domain
            if (!patterns.contains(host)) {
                patterns.add(host)
                savePatterns()
                MTSFlixLogger.log("PROVIDER", "⚡ Auto-detected new player pattern / stream host: $host. Added to extraction database.")
            }
        } catch (e: Exception) {
            // Ignore malformed URLs
        }
    }

    private fun savePatterns() {
        try {
            val array = JSONArray()
            for (p in patterns) {
                array.put(p)
            }
            patternFile?.writeText(array.toString(2))
        } catch (e: Exception) {
            Log.e("MTSFlix:Provider", "Error saving patterns: ${e.message}")
        }
    }
}
