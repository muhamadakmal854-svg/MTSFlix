package com.mts.mtsflix.auth

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

/**
 * MTSFlix Google Login Activity
 * Handshakes with Google Play Services and completes Firebase Authentication.
 */
class GoogleLoginActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var btnGoogle: Button
    private lateinit var tvMessage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set status bar dark
        window.statusBarColor = Color.parseColor("#0D0D0D")
        window.decorView.systemUiVisibility = 0

        buildUI()

        // Check if already signed in (just in case)
        if (GoogleSignInHelper.isSignedIn() && SecureSessionManager.isSessionValid(this)) {
            launchMainApp()
        }
    }

    private fun buildUI() {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.parseColor("#0D0D0D"))
        setContentView(scroll)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(80), dp(24), dp(40))
        }
        scroll.addView(root)

        // Logo
        val ivLogo = ImageView(this).apply {
            setImageResource(com.lagradost.cloudstream3.R.mipmap.ic_launcher)
            val lp = LinearLayout.LayoutParams(dp(90), dp(90))
            lp.bottomMargin = dp(20)
            layoutParams = lp
        }
        root.addView(ivLogo)

        val tvTitle = TextView(this).apply {
            text = "MTSFlix"
            textSize = 36f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#E50914"))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(4)
            layoutParams = lp
        }
        root.addView(tvTitle)

        val tvSubtitle = TextView(this).apply {
            text = "Log Masuk Google"
            textSize = 16f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(40)
            layoutParams = lp
        }
        root.addView(tvSubtitle)

        // Card
        val cardView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(28))
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A1A"))
                cornerRadius = dp(16).toFloat()
            }
            background = bg
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(24)
            layoutParams = lp
        }
        root.addView(cardView)

        tvStatus = TextView(this).apply {
            text = "Sila log masuk akaun Google"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(12)
            layoutParams = lp
        }
        cardView.addView(tvStatus)

        tvMessage = TextView(this).apply {
            text = "Akaun Google diperlukan untuk menyelaraskan watchlist, sejarah tontonan, dan mengaktifkan pemain video."
            textSize = 13f
            setTextColor(Color.parseColor("#AAAAAA"))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(20)
            layoutParams = lp
        }
        cardView.addView(tvMessage)

        progressBar = ProgressBar(this).apply {
            isIndeterminate = true
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32))
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.bottomMargin = dp(16)
            layoutParams = lp
        }
        cardView.addView(progressBar)

        // Google Sign In button (white background with grey/black text)
        btnGoogle = Button(this).apply {
            text = "Akaun Google"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1A1A1A"))
            val bg = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(12).toFloat()
            }
            background = bg
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
            setOnClickListener {
                startGoogleSignIn()
            }
        }
        cardView.addView(btnGoogle)

        // Footer
        val tvFooter = TextView(this).apply {
            text = "MTSFlix v1.0 • Hak Cipta © 2026 MTS"
            textSize = 11f
            setTextColor(Color.parseColor("#444444"))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(24)
            layoutParams = lp
        }
        root.addView(tvFooter)
    }

    private fun startGoogleSignIn() {
        progressBar.visibility = View.VISIBLE
        btnGoogle.isEnabled = false
        tvStatus.text = "Menghubungkan ke Google..."
        tvStatus.setTextColor(Color.parseColor("#FFA500"))

        val signInIntent = GoogleSignInHelper.getSignInIntent(this)
        startActivityForResult(signInIntent, GoogleSignInHelper.REQUEST_CODE_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == GoogleSignInHelper.REQUEST_CODE_SIGN_IN) {
            GoogleSignInHelper.handleSignInResultFromIntent(data, this,
                onSuccess = { account ->
                    tvStatus.text = "Mengesahkan akaun..."
                    lifecycleScope.launch {
                        GoogleSignInHelper.handleSignInResult(account,
                            onSuccess = { user ->
                                // Save session securely
                                val email = user.email ?: "user"
                                SecureSessionManager.saveSession(this@GoogleLoginActivity, email)
                                
                                // Initialize services
                                try {
                                    com.mts.mtsflix.MTSFlixInit.initialize(applicationContext)
                                } catch (e: Exception) {
                                    android.util.Log.e("MTSFlix", "Init error: ${e.message}")
                                }

                                tvStatus.text = "Log masuk berjaya! ✅"
                                tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                                progressBar.visibility = View.GONE
                                
                                lifecycleScope.launch {
                                    kotlinx.coroutines.delay(1000)
                                    launchMainApp()
                                }
                            },
                            onError = { e ->
                                showError("Ralat Firebase: ${e.message}")
                            }
                        )
                    }
                },
                onError = { e ->
                    val statusText = if (e is ApiException) {
                        "Ralat Google (Kod ${e.statusCode})"
                    } else {
                        e.message ?: "Log masuk dibatalkan"
                    }
                    showError(statusText)
                }
            )
        }
    }

    private fun showError(msg: String) {
        progressBar.visibility = View.GONE
        btnGoogle.isEnabled = true
        tvStatus.text = "Log Masuk Gagal"
        tvStatus.setTextColor(Color.parseColor("#FF5252"))
        tvMessage.text = msg
        tvMessage.setTextColor(Color.parseColor("#FF5252"))
    }

    private fun launchMainApp() {
        try {
            val intent = Intent(this, com.lagradost.cloudstream3.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("MTSFlix", "Error starting MainActivity: ${e.message}")
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
        }
        finish()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
