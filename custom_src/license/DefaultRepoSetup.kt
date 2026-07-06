package com.mts.mtsflix.license

import android.content.Context
import android.util.Log

/**
 * MTSFlix Default Repository Setup
 *
 * Automatically adds the MTS Provider repository to CloudStream's
 * extension list on first launch. The URL is permanently embedded
 * so users don't need to add it manually.
 *
 * Provider URL:
 * https://raw.githubusercontent.com/muhamadakmal854-svg/Provider/builds/plugins.json
 *
 * This uses SharedPreferences to inject the URL into CloudStream's
 * known preference keys for plugin repositories.
 */
object DefaultRepoSetup {

    private const val TAG = "MTSFlix:Repo"

    // The MTS Provider repository URL — HARDCODED & PERMANENT
    const val MTS_PROVIDER_URL =
        "https://cdn.jsdelivr.net/gh/muhamadakmal854-svg/Provider@builds/repo.json"

    // CloudStream's SharedPreferences name (known from CS3 source)
    private const val CS_PREF_NAME = "cloudstreamapp"

    // Known CloudStream preference keys for plugin repositories
    // (try all known keys across CS3 versions)
    private val REPO_PREF_KEYS = listOf(
        "PluginRepositories",
        "ExtensionRepos",
        "plugin_repos",
        "extension_repos",
        "CustomPluginRepos",
        "repositoryUrls"
    )

    private const val SETUP_DONE_KEY = "mtsflix_repo_setup_done"
    private const val MTSFLIX_PREF = "mtsflix_license"

    /**
     * Call this from MTSFlixInit.initialize(context)
     * Injects MTS Provider URL into CloudStream's preferences
     * on first launch, silently.
     */
    fun setup(context: Context) {
        val mtsPrefs = context.getSharedPreferences(MTSFLIX_PREF, Context.MODE_PRIVATE)

        // Only need to run once (on fresh install or after clear data)
        // But re-check each time in case user cleared CloudStream prefs
        val csPrefs = context.getSharedPreferences(CS_PREF_NAME, Context.MODE_PRIVATE)
        var injected = false

        for (key in REPO_PREF_KEYS) {
            val existing = csPrefs.getString(key, null)
            if (existing != null) {
                Log.d(TAG, "Found repo key: $key = $existing")
                if (!existing.contains(MTS_PROVIDER_URL)) {
                    // Inject our URL into existing list
                    val updated = injectUrl(existing, MTS_PROVIDER_URL)
                    csPrefs.edit().putString(key, updated).apply()
                    Log.i(TAG, "✅ MTS repo injected into key: $key")
                    injected = true
                } else {
                    Log.d(TAG, "MTS repo already present in key: $key")
                    injected = true
                }
            }
        }

        if (!injected) {
            // Key not found yet — CloudStream may not have created its prefs yet
            // Store our URL in a common format that covers most CS3 versions
            val jsonUrl = "[\"$MTS_PROVIDER_URL\"]"
            csPrefs.edit()
                .putString("PluginRepositories", jsonUrl)
                .putString("ExtensionRepos", jsonUrl)
                .apply()
            Log.i(TAG, "✅ MTS repo pre-set in CloudStream preferences")
        }

        Log.i(TAG, "Provider URL: $MTS_PROVIDER_URL")
    }

    /**
     * Injects the URL into a JSON array string
     * Handles formats: ["url1", "url2"] or url1,url2
     */
    private fun injectUrl(existing: String, url: String): String {
        return try {
            // Try JSON array format
            val trimmed = existing.trim()
            if (trimmed.startsWith("[")) {
                val inner = trimmed.removePrefix("[").removeSuffix("]")
                val urls = inner.split(",").map { it.trim().trim('"') }
                    .filter { it.isNotBlank() }.toMutableList()
                if (!urls.contains(url)) urls.add(0, url)
                "[${urls.joinToString(",") { "\"$it\"" }}]"
            } else {
                // Plain comma-separated
                val urls = existing.split(",").map { it.trim() }
                    .filter { it.isNotBlank() }.toMutableList()
                if (!urls.contains(url)) urls.add(0, url)
                urls.joinToString(",")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse existing repos: ${e.message}")
            // Fallback: append
            "$existing,$url"
        }
    }
}
