package com.mts.mtsflix.license

import android.animation.ObjectAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * MTSFlix License Check Activity
 *
 * This is the LAUNCHER activity. It:
 * 1. Generates/shows device code
 * 2. Verifies against GitHub licenses.json
 * 3. If valid → launches CloudStream MainActivity
 * 4. If not → shows device code + contact admin instructions
 *
 * UI: Dark theme, premium design, built programmatically (no XML layout needed)
 */
class LicenseCheckActivity : AppCompatActivity() {

    // ─── UI References ────────────────────────────────────────────────────────
    private lateinit var tvStatus: TextView
    private lateinit var tvDeviceCode: TextView
    private lateinit var tvMessage: TextView
    private lateinit var btnVerify: Button
    private lateinit var btnContact: Button
    private lateinit var btnCopy: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var cardView: LinearLayout
    private lateinit var tvExpiry: TextView
    private lateinit var tvUsername: TextView

    private val ADMIN_CONTACT = "https://t.me/mtsadm"

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set status bar dark
        window.statusBarColor = Color.parseColor("#0D0D0D")
        window.decorView.systemUiVisibility = 0

        buildUI()

        // Initialize MTSFlix services (Firebase, notifications, extension repo setup)
        try {
            com.mts.mtsflix.MTSFlixInit.initialize(applicationContext)
        } catch (e: Exception) {
            android.util.Log.e("MTSFlix", "Initialization error: ${e.message}")
        }

        val deviceCode = DeviceCodeManager.getDeviceCode(this)
        tvDeviceCode.text = deviceCode

        // Auto-verify on first open
        startVerification(deviceCode)
    }

    // ─── Verification Logic ───────────────────────────────────────────────────

    private fun startVerification(deviceCode: String) {
        setLoadingState("Menyemak lesen peranti...")

        lifecycleScope.launch {
            // Small delay for UX
            delay(500)

            val result = LicenseVerifier.verify(deviceCode)

            when (result.status) {
                LicenseVerifier.Status.VALID -> {
                    DeviceCodeManager.setVerified(this@LicenseCheckActivity, result.username, result.expiryDate)
                    showVerifiedState(result.username, result.expiryDate)
                    delay(2000)
                    checkGoogleLoginAndNavigate()
                }

                LicenseVerifier.Status.BANNED -> {
                    DeviceCodeManager.clearVerification(this@LicenseCheckActivity)
                    showErrorState(
                        icon = "🚫",
                        title = "Peranti Disekat",
                        message = result.message,
                        showContact = true
                    )
                }

                LicenseVerifier.Status.EXPIRED -> {
                    DeviceCodeManager.clearVerification(this@LicenseCheckActivity)
                    showErrorState(
                        icon = "⏰",
                        title = "Lesen Tamat Tempoh",
                        message = result.message,
                        showContact = true
                    )
                }

                LicenseVerifier.Status.INACTIVE -> {
                    DeviceCodeManager.clearVerification(this@LicenseCheckActivity)
                    showErrorState(
                        icon = "🚫",
                        title = "Lesen Tidak Aktif",
                        message = result.message,
                        showContact = true
                    )
                }

                LicenseVerifier.Status.NOT_FOUND -> {
                    DeviceCodeManager.clearVerification(this@LicenseCheckActivity)
                    showErrorState(
                        icon = "🔑",
                        title = "Peranti Belum Didaftarkan",
                        message = result.message,
                        showContact = true
                    )
                }

                LicenseVerifier.Status.NETWORK_ERROR -> {
                    if (DeviceCodeManager.isVerifiedLocally(this@LicenseCheckActivity)) {
                        val username = DeviceCodeManager.getUsername(this@LicenseCheckActivity) ?: "User"
                        val expiry = DeviceCodeManager.getExpiryDate(this@LicenseCheckActivity) ?: ""
                        showVerifiedState(username, expiry)
                        delay(1200)
                        checkGoogleLoginAndNavigate()
                    } else {
                        showErrorState(
                            icon = "📡",
                            title = "Tiada Sambungan",
                            message = result.message,
                            showContact = false
                        )
                    }
                }

                LicenseVerifier.Status.PARSE_ERROR -> {
                    if (DeviceCodeManager.isVerifiedLocally(this@LicenseCheckActivity)) {
                        val username = DeviceCodeManager.getUsername(this@LicenseCheckActivity) ?: "User"
                        val expiry = DeviceCodeManager.getExpiryDate(this@LicenseCheckActivity) ?: ""
                        showVerifiedState(username, expiry)
                        delay(1200)
                        checkGoogleLoginAndNavigate()
                    } else {
                        showErrorState(
                            icon = "⚠️",
                            title = "Ralat Sistem",
                            message = result.message,
                            showContact = true
                        )
                    }
                }
            }
        }
    }

    private fun launchMainApp() {
        // FLAG_ACTIVITY_CLEAR_TASK wipes the entire back stack so the user
        // can never navigate back to LicenseCheckActivity from the main app.
        val intent = Intent(this, com.lagradost.cloudstream3.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun checkGoogleLoginAndNavigate() {
        markSetupComplete()
        if (com.mts.mtsflix.auth.GoogleSignInHelper.isSignedIn() && 
            com.mts.mtsflix.auth.SecureSessionManager.isSessionValid(this)) {
            launchMainApp()
        } else {
            launchGoogleLogin()
        }
    }

    private fun markSetupComplete() {
        // CloudStream DataStore uses SharedPreferences file named "rebuild_preference"
        // (PREFERENCES_NAME in DataStore.kt). HAS_DONE_SETUP_KEY = "HAS_DONE_SETUP".
        val key = "HAS_DONE_SETUP"
        try {
            getSharedPreferences("rebuild_preference", android.content.Context.MODE_PRIVATE)
                .edit().putBoolean(key, true).apply()
        } catch (e: Exception) { android.util.Log.w("MTSFlix", "setup bypass fail: ${e.message}") }
        try {
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                .edit().putBoolean(key, true).apply()
        } catch (e: Exception) { android.util.Log.w("MTSFlix", "setup bypass2 fail: ${e.message}") }
    }

    private fun launchGoogleLogin() {
        val intent = Intent(this, com.mts.mtsflix.auth.GoogleLoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    // ─── UI State Management ──────────────────────────────────────────────────

    private fun setLoadingState(message: String) {
        progressBar.visibility = View.VISIBLE
        btnVerify.visibility = View.VISIBLE
        btnVerify.isEnabled = false
        btnVerify.text = "Menyemak lesen..."
        btnContact.visibility = View.GONE
        tvStatus.text = message
        tvStatus.setTextColor(Color.parseColor("#FFA500"))
        tvMessage.visibility = View.GONE
        tvUsername.visibility = View.GONE
        tvExpiry.visibility = View.GONE
        btnCopy.visibility = View.VISIBLE
    }

    private fun showVerifiedState(username: String, expiryDate: String) {
        progressBar.visibility = View.GONE
        btnVerify.visibility = View.GONE // Hide verify button on success
        btnContact.visibility = View.GONE
        tvStatus.text = "✅ Lesen Disahkan!"
        tvStatus.setTextColor(Color.parseColor("#4CAF50"))
        tvMessage.visibility = View.VISIBLE
        tvMessage.text = "Selamat datang ke MTSFlix, $username!"
        tvMessage.setTextColor(Color.parseColor("#CCCCCC"))
        tvUsername.visibility = View.VISIBLE
        tvUsername.text = "👤  $username"
        tvExpiry.visibility = View.VISIBLE
        tvExpiry.text = "📅  Sah sehingga: $expiryDate"
        btnCopy.visibility = View.GONE
    }

    private fun showErrorState(icon: String, title: String, message: String, showContact: Boolean) {
        progressBar.visibility = View.GONE
        btnVerify.visibility = View.VISIBLE
        btnVerify.isEnabled = true
        btnVerify.text = if (icon == "📡") "🔄 Cuba Semula" else "🔑 Semak Lesen"
        btnVerify.setOnClickListener {
            val code = DeviceCodeManager.getDeviceCode(this@LicenseCheckActivity)
            startVerification(code)
        }

        tvStatus.text = "$icon $title"
        tvStatus.setTextColor(Color.parseColor("#FF5252"))
        tvMessage.visibility = View.VISIBLE
        tvMessage.text = message
        tvMessage.setTextColor(Color.parseColor("#AAAAAA"))
        tvUsername.visibility = View.GONE
        tvExpiry.visibility = View.GONE
        btnCopy.visibility = View.VISIBLE

        if (showContact) {
            btnContact.visibility = View.VISIBLE
        } else {
            btnContact.visibility = View.GONE
        }
    }

    // ─── Build UI Programmatically ────────────────────────────────────────────

    private fun buildUI() {
        // Root scroll
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.parseColor("#0D0D0D"))
        setContentView(scroll)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(60), dp(24), dp(40))
        }
        scroll.addView(root)

        // ── Logo / Title ──────────────────────────────────────────────────────
        val ivLogo = ImageView(this).apply {
            setImageResource(com.lagradost.cloudstream3.R.mipmap.ic_launcher)
            val lp = LinearLayout.LayoutParams(dp(80), dp(80))
            lp.bottomMargin = dp(16)
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
            text = "Pengesahan Peranti"
            textSize = 14f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(40)
            layoutParams = lp
        }
        root.addView(tvSubtitle)

        // ── Card ──────────────────────────────────────────────────────────────
        cardView = LinearLayout(this).apply {
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

        // ── Card content: Device Code label ───────────────────────────────────
        val tvDeviceLabel = TextView(this).apply {
            text = "Kod Peranti Anda"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            letterSpacing = 0.1f
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(8)
            layoutParams = lp
        }
        cardView.addView(tvDeviceLabel)

        // ── Device Code (large, monospace, red) ───────────────────────────────
        tvDeviceCode = TextView(this).apply {
            text = "MTSF-XXXX-XXXX-XXXX"
            textSize = 24f
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(Color.parseColor("#E50914"))
            gravity = Gravity.CENTER
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#0D0D0D"))
                cornerRadius = dp(8).toFloat()
            }
            background = bg
            setPadding(dp(16), dp(16), dp(16), dp(16))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(12)
            layoutParams = lp
        }
        cardView.addView(tvDeviceCode)

        // ── Copy button ───────────────────────────────────────────────────────
        btnCopy = Button(this).apply {
            text = "📋 Salin Kod"
            textSize = 13f
            setTextColor(Color.parseColor("#CCCCCC"))
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#2A2A2A"))
                cornerRadius = dp(8).toFloat()
            }
            background = bg
            setPadding(dp(16), dp(10), dp(16), dp(10))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(20)
            layoutParams = lp
            setOnClickListener {
                val code = tvDeviceCode.text.toString()
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("MTSFlix Device Code", code))
                Toast.makeText(context, "Kod disalin! ✅", Toast.LENGTH_SHORT).show()
            }
        }
        cardView.addView(btnCopy)

        // ── Divider ───────────────────────────────────────────────────────────
        val divider = View(this).apply {
            setBackgroundColor(Color.parseColor("#2A2A2A"))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            lp.bottomMargin = dp(20)
            layoutParams = lp
        }
        cardView.addView(divider)

        // ── Status Text ───────────────────────────────────────────────────────
        tvStatus = TextView(this).apply {
            text = "Menyemak lesen..."
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FFA500"))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(8)
            layoutParams = lp
        }
        cardView.addView(tvStatus)

        // ── Username (shown when verified) ────────────────────────────────────
        tvUsername = TextView(this).apply {
            text = ""
            textSize = 14f
            setTextColor(Color.parseColor("#4CAF50"))
            gravity = Gravity.CENTER
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(4)
            layoutParams = lp
        }
        cardView.addView(tvUsername)

        // ── Expiry (shown when verified) ──────────────────────────────────────
        tvExpiry = TextView(this).apply {
            text = ""
            textSize = 13f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(8)
            layoutParams = lp
        }
        cardView.addView(tvExpiry)

        // ── Message ───────────────────────────────────────────────────────────
        tvMessage = TextView(this).apply {
            text = ""
            textSize = 13f
            setTextColor(Color.parseColor("#AAAAAA"))
            gravity = Gravity.CENTER
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(8)
            layoutParams = lp
        }
        cardView.addView(tvMessage)

        // ── Progress Bar ──────────────────────────────────────────────────────
        progressBar = ProgressBar(this).apply {
            isIndeterminate = true
            visibility = View.VISIBLE
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32))
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.topMargin = dp(8)
            layoutParams = lp
        }
        cardView.addView(progressBar)

        // ── Verify / Retry / Contact Button ───────────────────────────────────
        btnVerify = Button(this).apply {
            text = "🔑 Semak Lesen"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#E50914"))
                cornerRadius = dp(12).toFloat()
            }
            background = bg
            isEnabled = false
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(16)
            layoutParams = lp
            setOnClickListener {
                val code = DeviceCodeManager.getDeviceCode(this@LicenseCheckActivity)
                startVerification(code)
            }
        }
        cardView.addView(btnVerify)

        // ── Contact Admin Button (initially hidden) ───────────────────────────
        btnContact = Button(this).apply {
            text = "📞 Hubungi Admin"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#2A2A2A"))
                cornerRadius = dp(12).toFloat()
            }
            background = bg
            visibility = View.GONE
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(12)
            layoutParams = lp
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ADMIN_CONTACT)))
                } catch (e: Exception) {
                    Toast.makeText(context, "Telegram tidak dijumpai", Toast.LENGTH_SHORT).show()
                }
            }
        }
        cardView.addView(btnContact)

        // ── Footer ────────────────────────────────────────────────────────────
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

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
