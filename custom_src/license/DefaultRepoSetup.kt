package com.mts.mtsflix.license

import android.content.Context
import android.util.Log
import com.mts.mtsflix.MTSFlixLogger
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * MTSFlix Default Repository Setup
 *
 * Automatically adds the MTS Provider repository to CloudStream's
 * extension list. It performs duplicate removal, URL structure validation,
 * response code check, and caches the repository for offline use.
 */
object DefaultRepoSetup {

    private const val TAG = "MTSFlix:Repo"

    // The MTS Provider repository URL — HARDCODED & PERMANENT
    const val MTS_PROVIDER_URL =
        "https://cdn.jsdelivr.net/gh/muhamadakmal854-svg/Provider@builds/repo.json"

    // CloudStream's SharedPreferences name
    private const val CS_PREF_NAME = "cloudstreamapp"

    // Known CloudStream preference keys for plugin repositories
    private val REPO_PREF_KEYS = listOf(
        "PluginRepositories",
        "ExtensionRepos",
        "plugin_repos",
        "extension_repos",
        "CustomPluginRepos",
        "repositoryUrls"
    )

    private const val CACHE_FILE_NAME = "mtsflix_extension_cache.json"

    fun setup(context: Context) {
        // 1. Clean list, inject provider URL, and strip duplicate URLs
        cleanAndInjectPrefs(context)

        // 2. Validate URL online and handle cache/offline fallbacks
        validateAndCache(context)
    }

    private fun cleanAndInjectPrefs(context: Context) {
        val csPrefs = context.getSharedPreferences(CS_PREF_NAME, Context.MODE_PRIVATE)
        val editor = csPrefs.edit()

        for (key in REPO_PREF_KEYS) {
            val existing = csPrefs.getString(key, null)
            if (existing != null) {
                val cleaned = cleanList(existing)
                editor.putString(key, cleaned)
            }
        }
        
        // Ensure default fallback keys are seeded
        val defaultJson = "[\"$MTS_PROVIDER_URL\"]"
        if (csPrefs.getString("PluginRepositories", null) == null) {
            editor.putString("PluginRepositories", defaultJson)
        }
        if (csPrefs.getString("ExtensionRepos", null) == null) {
            editor.putString("ExtensionRepos", defaultJson)
        }
        editor.apply()
    }

    private fun cleanList(existing: String): String {
        return try {
            val trimmed = existing.trim()
            if (trimmed.startsWith("[")) {
                val inner = trimmed.removePrefix("[").removeSuffix("]")
                val urls = inner.split(",")
                    .map { it.trim().trim('"') }
                    .filter { it.isNotBlank() && it.startsWith("http") }
                    .distinct() // Eliminate duplicate entries
                    .toMutableList()
                
                if (!urls.contains(MTS_PROVIDER_URL)) {
                    urls.add(0, MTS_PROVIDER_URL)
                }
                "[${urls.joinToString(",") { "\"$it\"" }}]"
            } else {
                val urls = existing.split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it.startsWith("http") }
                    .distinct() // Eliminate duplicate entries
                    .toMutableList()
                
                if (!urls.contains(MTS_PROVIDER_URL)) {
                    urls.add(0, MTS_PROVIDER_URL)
                }
                urls.joinToString(",")
            }
        } catch (e: Exception) {
            MTSFlixLogger.log("EXTENSION", "Failed cleaning repo list: ${e.message}")
            existing
        }
    }

    private fun validateAndCache(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!isValidUrl(MTS_PROVIDER_URL)) {
                    MTSFlixLogger.log("EXTENSION", "❌ Embedded URL structure is invalid: $MTS_PROVIDER_URL")
                    loadCache(context)
                    return@launch
                }

                // Verify connection
                val connection = URL(MTS_PROVIDER_URL).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "GET"
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val content = connection.inputStream.bufferedReader().use { it.readText() }
                    if (content.contains("name") || content.contains("url")) {
                        MTSFlixLogger.log("EXTENSION", "✅ Provider URL validated successfully: 200 OK")
                        saveCache(context, content)
                    } else {
                        MTSFlixLogger.log("EXTENSION", "⚠️ Received invalid JSON content from Provider URL, loading cache fallback.")
                        loadCache(context)
                    }
                } else {
                    MTSFlixLogger.log("EXTENSION", "❌ Provider URL broken: HTTP $responseCode, loading cache fallback.")
                    loadCache(context)
                }
            } catch (e: Exception) {
                MTSFlixLogger.log("EXTENSION", "❌ Failed to validate Provider URL (Network offline?): ${e.message}")
                loadCache(context)
            }
        }
    }

    private fun isValidUrl(urlString: String): Boolean {
        return try {
            val url = URL(urlString)
            url.protocol == "http" || url.protocol == "https"
        } catch (e: Exception) {
            false
        }
    }

    private fun saveCache(context: Context, content: String) {
        try {
            val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)
            cacheFile.writeText(content)
            MTSFlixLogger.log("EXTENSION", "✅ Provider configuration cached successfully.")
        } catch (e: Exception) {
            MTSFlixLogger.log("EXTENSION", "Failed to save provider cache: ${e.message}")
        }
    }

    fun loadCache(context: Context): String? {
        return try {
            val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)
            if (cacheFile.exists()) {
                val content = cacheFile.readText()
                MTSFlixLogger.log("EXTENSION", "✅ Restored provider configuration from local cache fallback.")
                content
            } else {
                MTSFlixLogger.log("EXTENSION", "No local cache fallback available.")
                null
            }
        } catch (e: Exception) {
            MTSFlixLogger.log("EXTENSION", "Failed to load cache fallback: ${e.message}")
            null
        }
    }
}
