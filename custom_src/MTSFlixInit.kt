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

        Log.i(TAG, "═══════════════════════════════")
        Log.i(TAG, "   MTSFlix Initializing...")
        Log.i(TAG, "═══════════════════════════════")

        // 1. Firebase
        initFirebase(context)

        // 2. Notifications
        try {
            EpisodeNotificationWorker.createNotificationChannel(context)
            Log.i(TAG, "✅ Notification channel ready")
        } catch (e: Exception) {
            Log.w(TAG, "Notification channel: ${e.message}")
        }

        // 3. Inject MTS Provider URL into CloudStream prefs
        try {
            DefaultRepoSetup.setup(context)
            Log.i(TAG, "✅ Provider URL injected: ${DefaultRepoSetup.MTS_PROVIDER_URL}")
        } catch (e: Exception) {
            Log.w(TAG, "Provider setup: ${e.message}")
        }

        // 4. If user already signed in, start services
        if (GoogleSignInHelper.isSignedIn()) {
            onUserSignedIn(context)
        } else {
            Log.i(TAG, "ℹ️  User not signed in — standby mode")
        }

        Log.i(TAG, "✅ MTSFlix initialized successfully")
    }

    // ─── Firebase ─────────────────────────────────────────────────────────────

    private fun initFirebase(context: Context) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
                Log.i(TAG, "✅ Firebase initialized (mtsflix-592e4)")
            } else {
                Log.d(TAG, "Firebase already initialized")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase skipped: ${e.message}")
        }
    }

    // ─── User Signed In ───────────────────────────────────────────────────────

    fun onUserSignedIn(context: Context) {
        Log.i(TAG, "👤 User: ${GoogleSignInHelper.getUserEmail()}")

        // Start episode tracker
        try {
            EpisodeNotificationWorker.schedule(context)
            Log.i(TAG, "✅ Episode tracker started (every 12h)")
        } catch (e: Exception) {
            Log.e(TAG, "Episode tracker: ${e.message}")
        }

        // Save user profile to Firestore
        try {
            FirestoreWatchlistSync.saveUserProfile(
                displayName = GoogleSignInHelper.getUserDisplayName(),
                email = GoogleSignInHelper.getUserEmail(),
                photoUrl = GoogleSignInHelper.getUserPhotoUrl()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Profile sync: ${e.message}")
        }
    }

    // ─── User Signed Out ──────────────────────────────────────────────────────

    fun onUserSignedOut(context: Context) {
        Log.i(TAG, "👤 User signed out")
        try {
            EpisodeNotificationWorker.cancel(context)
        } catch (e: Exception) {
            Log.e(TAG, "Episode tracker cancel: ${e.message}")
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
