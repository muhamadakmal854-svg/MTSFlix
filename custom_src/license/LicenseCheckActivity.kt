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
    private lateinit var btnCopy: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var cardView: LinearLayout
    private lateinit var tvExpiry: TextView
    private lateinit var tvUsername: TextView

    private val ADMIN_CONTACT = "https://t.me/muhamadakmal854" // Update with your Telegram/WhatsApp

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
                    launchMainApp()
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
                        launchMainApp()
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
                        launchMainApp()
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
        try {
            // Start CloudStream's MainActivity directly (compile-time checked, R8/ProGuard safe)
            val intent = Intent(this, com.lagradost.cloudstream3.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("MTSFlix", "Error launching MainActivity: ${e.message}")
            // Fallback: restart the app
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
        }
        finish()
    }

    // ─── UI State Management ──────────────────────────────────────────────────

    private fun setLoadingState(message: String) {
        progressBar.visibility = View.VISIBLE
        btnVerify.visibility = View.VISIBLE
        btnVerify.isEnabled = false
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
        tvStatus.text = "$icon $title"
        tvStatus.setTextColor(Color.parseColor("#FF5252"))
        tvMessage.visibility = View.VISIBLE
        tvMessage.text = message
        tvMessage.setTextColor(Color.parseColor("#AAAAAA"))
        tvUsername.visibility = View.GONE
        tvExpiry.visibility = View.GONE
        btnCopy.visibility = View.VISIBLE

        if (showContact) {
            btnVerify.text = "📞 Hubungi Admin"
            btnVerify.setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ADMIN_CONTACT)))
                } catch (e: Exception) {
                    Toast.makeText(this, "Telegram tidak dijumpai", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            btnVerify.text = "🔄 Cuba Semula"
            btnVerify.setOnClickListener {
                val deviceCode = DeviceCodeManager.getDeviceCode(this)
                startVerification(deviceCode)
            }
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
        val tvLogo = TextView(this).apply {
            text = "🎬"
            textSize = 64f
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(8)
            layoutParams = lp
        }
        root.addView(tvLogo)

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
            text = "🔄 Cuba Semula"
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
                text = "🔄 Cuba Semula"
                setOnClickListener {
                    val code = DeviceCodeManager.getDeviceCode(this@LicenseCheckActivity)
                    startVerification(code)
                }
                val code = DeviceCodeManager.getDeviceCode(this@LicenseCheckActivity)
                startVerification(code)
            }
        }
        cardView.addView(btnVerify)

        // ── Footer ────────────────────────────────────────────────────────────
        val tvFooter = TextView(this).apply {
            text = "MTSFlix v1.0 • Powered by CloudStream 3\nHak Cipta © 2025 MTS"
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
