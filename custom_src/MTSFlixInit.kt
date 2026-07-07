package com.mts.mtsflix

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.mts.mtsflix.auth.GoogleSignInHelper
import com.mts.mtsflix.license.DefaultRepoSetup
import com.mts.mtsflix.notifications.EpisodeNotificationWorker
import com.mts.mtsflix.sync.FirestoreWatchlistSync

/**
 * MTSFlix App Initializer
 * Call MTSFlixInit.initialize(context) from Application.onCreate()
 *
 * Order of initialization:
 * 1. Firebase SDK
 * 2. Notification channels
 * 3. Default provider repository (MTS)
 * 4. Episode tracker (if signed in)
 * 5. Watchlist sync (if signed in)
 */
object MTSFlixInit {

    private const val TAG = "MTSFlix:Init"
    private var initialized = false

    // ─── Initialization ───────────────────────────────────────────────────────

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true

        // Initialize file logging first
        MTSFlixLogger.init(context)

        // Initialize video provider engine
        VideoProviderEngine.init(context)

        MTSFlixLogger.log("SYSTEM", "═══════════════════════════════")
        MTSFlixLogger.log("SYSTEM", "   MTSFlix Initializing...")
        MTSFlixLogger.log("SYSTEM", "═══════════════════════════════")

        // 1. Firebase
        initFirebase(context)

        // 2. Notifications
        try {
            EpisodeNotificationWorker.createNotificationChannel(context)
            MTSFlixLogger.log("SYSTEM", "✅ Notification channel ready")
        } catch (e: Exception) {
            MTSFlixLogger.log("SYSTEM", "Notification channel error: ${e.message}")
        }

        // 3. Inject MTS Provider URL into CloudStream prefs
        try {
            DefaultRepoSetup.setup(context)
            MTSFlixLogger.log("EXTENSION", "✅ Provider URL injected: ${DefaultRepoSetup.MTS_PROVIDER_URL}")
        } catch (e: Exception) {
            MTSFlixLogger.log("EXTENSION", "Provider setup error: ${e.message}")
        }

        // 4. If user already signed in, start services
        if (GoogleSignInHelper.isSignedIn()) {
            onUserSignedIn(context)
        } else {
            MTSFlixLogger.log("AUTH", "ℹ️ User not signed in — standby mode")
        }

        MTSFlixLogger.log("SYSTEM", "✅ MTSFlix initialized successfully")
    }

    // ─── Firebase ─────────────────────────────────────────────────────────────

    private fun initFirebase(context: Context) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
                MTSFlixLogger.log("SYSTEM", "✅ Firebase initialized (mtsflix-592e4)")
            } else {
                MTSFlixLogger.log("SYSTEM", "Firebase already initialized")
            }
        } catch (e: Exception) {
            MTSFlixLogger.log("SYSTEM", "Firebase skipped: ${e.message}")
        }
    }

    // ─── User Signed In ───────────────────────────────────────────────────────

    fun onUserSignedIn(context: Context) {
        MTSFlixLogger.log("AUTH", "👤 User: ${GoogleSignInHelper.getUserEmail()}")

        // Start episode tracker
        try {
            EpisodeNotificationWorker.schedule(context)
            MTSFlixLogger.log("SYSTEM", "✅ Episode tracker started (every 12h)")
        } catch (e: Exception) {
            MTSFlixLogger.log("SYSTEM", "Episode tracker error: ${e.message}")
        }

        // Save user profile to Firestore
        try {
            FirestoreWatchlistSync.saveUserProfile(
                displayName = GoogleSignInHelper.getUserDisplayName(),
                email = GoogleSignInHelper.getUserEmail(),
                photoUrl = GoogleSignInHelper.getUserPhotoUrl()
            )
        } catch (e: Exception) {
            MTSFlixLogger.log("SYNC", "Profile sync error: ${e.message}")
        }
    }

    // ─── User Signed Out ──────────────────────────────────────────────────────

    fun onUserSignedOut(context: Context) {
        MTSFlixLogger.log("AUTH", "👤 User signed out")
        try {
            EpisodeNotificationWorker.cancel(context)
        } catch (e: Exception) {
            MTSFlixLogger.log("SYSTEM", "Episode tracker cancel error: ${e.message}")
        }
    }

    // ─── Status ───────────────────────────────────────────────────────────────

    fun isInitialized() = initialized

    fun getStatus(context: Context): Map<String, Any> = mapOf(
        "initialized" to initialized,
        "userSignedIn" to GoogleSignInHelper.isSignedIn(),
        "userEmail" to (GoogleSignInHelper.getUserEmail() ?: "none"),
        "providerUrl" to DefaultRepoSetup.MTS_PROVIDER_URL
    )
}
