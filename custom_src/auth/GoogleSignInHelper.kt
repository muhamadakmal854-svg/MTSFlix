package com.mts.mtsflix.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/**
 * MTSFlix Google Sign-In Helper
 * Handles all Google authentication via Firebase Auth
 */
object GoogleSignInHelper {

    private const val TAG = "MTSFlix:Auth"
    
    private var _googleSignInClient: GoogleSignInClient? = null
    
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    // ─── Client Setup ─────────────────────────────────────────────────────────

    fun getSignInClient(context: Context): GoogleSignInClient? {
        if (_googleSignInClient == null) {
            try {
                val clientId = getWebClientId(context)
                if (clientId.isEmpty()) {
                    Log.e(TAG, "❌ Google Sign-In Web Client ID is empty. Google Auth is unconfigured.")
                    return null
                }
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(clientId)
                    .requestEmail()
                    .requestProfile()
                    .build()
                _googleSignInClient = GoogleSignIn.getClient(context.applicationContext, gso)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to initialize GoogleSignInClient: ${e.message}")
                return null
            }
        }
        return _googleSignInClient
    }

    fun getSignInIntent(context: Context): Intent? {
        return getSignInClient(context)?.signInIntent
    }

    // ─── Auth State ───────────────────────────────────────────────────────────

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    fun isSignedIn(): Boolean = auth.currentUser != null

    fun getUserDisplayName(): String? = auth.currentUser?.displayName

    fun getUserEmail(): String? = auth.currentUser?.email

    fun getUserPhotoUrl(): String? = auth.currentUser?.photoUrl?.toString()

    fun getUserUid(): String? = auth.currentUser?.uid

    // ─── Sign In ──────────────────────────────────────────────────────────────

    /**
     * Call this in onActivityResult with REQUEST_CODE_SIGN_IN
     * Returns success/failure via callbacks
     */
    suspend fun handleSignInResult(
        account: GoogleSignInAccount?,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (account == null) {
            onError(Exception("Google Sign-In cancelled or failed"))
            return
        }
        
        try {
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            val result = auth.signInWithCredential(credential).await()
            result.user?.let { user ->
                Log.i(TAG, "✅ Signed in as: ${user.displayName} (${user.email})")
                onSuccess(user)
            } ?: onError(Exception("User is null after sign-in"))
        } catch (e: Exception) {
            Log.e(TAG, "❌ Sign-in failed: ${e.message}")
            onError(e)
        }
    }

    fun handleSignInResultFromIntent(
        data: Intent?,
        context: Context,
        onSuccess: (GoogleSignInAccount) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.result
            onSuccess(account)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to get account from intent: ${e.message}")
            onError(e)
        }
    }

    // ─── Sign Out ─────────────────────────────────────────────────────────────

    fun signOut(context: Context, onComplete: () -> Unit = {}) {
        auth.signOut()
        getSignInClient(context).signOut().addOnCompleteListener {
            Log.i(TAG, "✅ Signed out")
            onComplete()
        }
    }

    // ─── Auth State Listener ──────────────────────────────────────────────────

    fun addAuthStateListener(listener: FirebaseAuth.AuthStateListener) {
        auth.addAuthStateListener(listener)
    }

    fun removeAuthStateListener(listener: FirebaseAuth.AuthStateListener) {
        auth.removeAuthStateListener(listener)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun getWebClientId(context: Context): String {
        // Try to get from resources (set by google-services plugin)
        return try {
            val resourceId = context.resources.getIdentifier(
                "default_web_client_id", "string", context.packageName
            )
            if (resourceId != 0) context.getString(resourceId) else ""
        } catch (e: Exception) {
            Log.w(TAG, "Could not find default_web_client_id: ${e.message}")
            ""
        }
    }

    const val REQUEST_CODE_SIGN_IN = 9001
}
