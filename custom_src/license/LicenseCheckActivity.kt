package com.mts.mtsflix.license

import android.accounts.AccountManager
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
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MTSFlix License Check & Google Sign-In Activity v2.0
 */
class LicenseCheckActivity : AppCompatActivity() {

    private val REQUEST_CODE_GOOGLE_PICKER = 9005

    // ─── UI References ────────────────────────────────────────────────────────
    private lateinit var tvStatus: TextView
    private lateinit var tvDeviceCode: TextView
    private lateinit var tvMessage: TextView
    private lateinit var btnVerify: Button
    private lateinit var btnContact: Button
    private lateinit var btnCopy: Button
    private lateinit var btnGoogleSignIn: Button
    private lateinit var btnSkipGoogle: Button
    private lateinit var btnQrPairing: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var cardView: LinearLayout
    private lateinit var tvExpiry: TextView
    private lateinit var tvUsername: TextView

    private val ADMIN_CONTACT = "https://t.me/mtsadm"

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    private var isLaunching = false  // Guard against double-launch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
            if (uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION) {
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                val isTablet = (resources.configuration.screenLayout and 
                        android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK) >= 
                        android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE
                if (isTablet) {
                    requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                } else {
                    requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            }
        } catch (e: Exception) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        window.statusBarColor = Color.parseColor("#0D0D0D")
        window.decorView.systemUiVisibility = 0

        buildUI()

        try {
            com.mts.mtsflix.MTSFlixInit.initialize(applicationContext)
        } catch (e: Exception) {
            android.util.Log.e("MTSFlix", "Initialization error: ${e.message}")
        }

        val deviceCode = DeviceCodeManager.getDeviceCode(this)
        tvDeviceCode.text = deviceCode

        // ── Fast-path: already verified locally within 24h — skip network call ──
        if (DeviceCodeManager.isVerifiedLocally(this)) {
            val username = DeviceCodeManager.getUsername(this) ?: "User"
            val expiry  = DeviceCodeManager.getExpiryDate(this) ?: ""
            showVerifiedState(username, expiry)
            lifecycleScope.launch(Dispatchers.Main) {
                delay(800)
                checkGoogleLoginAndNavigate(username)
            }
            return
        }

        startVerification(deviceCode)
    }

    // ─── Verification Logic ───────────────────────────────────────────────────

    private fun startVerification(deviceCode: String) {
        setLoadingState("Menyemak lesen peranti...")

        lifecycleScope.launch {
            delay(500)

            val result = LicenseVerifier.verify(deviceCode)

            when (result.status) {
                LicenseVerifier.Status.VALID -> {
                    DeviceCodeManager.setVerified(this@LicenseCheckActivity, result.username, result.expiryDate)
                    showVerifiedState(result.username, result.expiryDate)
                    delay(1500)
                    checkGoogleLoginAndNavigate(result.username)
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
                    if (DeviceCodeManager.isVerifiedLocallyOffline(this@LicenseCheckActivity)) {
                        val username = DeviceCodeManager.getUsername(this@LicenseCheckActivity) ?: "User"
                        val expiry = DeviceCodeManager.getExpiryDate(this@LicenseCheckActivity) ?: ""
                        showVerifiedState(username, expiry)
                        delay(1000)
                        checkGoogleLoginAndNavigate(username)
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
                    if (DeviceCodeManager.isVerifiedLocallyOffline(this@LicenseCheckActivity)) {
                        val username = DeviceCodeManager.getUsername(this@LicenseCheckActivity) ?: "User"
                        val expiry = DeviceCodeManager.getExpiryDate(this@LicenseCheckActivity) ?: ""
                        showVerifiedState(username, expiry)
                        delay(1000)
                        checkGoogleLoginAndNavigate(username)
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
        if (isLaunching) return  // Prevent double-launch
        isLaunching = true

        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val savedProfiles = prefs.getString(com.mts.mtsflix.license.ProfileSwitchActivity.KEY_PROFILES, null)

        // If no profiles saved yet, go directly to MainActivity (skip profile screen)
        if (savedProfiles.isNullOrBlank()) {
            val intent = Intent(this, com.lagradost.cloudstream3.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        } else {
            // Profiles exist — show Profile Picker screen
            val intent = Intent(this, com.mts.mtsflix.license.ProfileSwitchActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    private fun checkGoogleLoginAndNavigate(username: String) {
        markSetupComplete()

        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val savedGoogleEmail = prefs.getString("GOOGLE_ACCOUNT_EMAIL", null)

        if (!savedGoogleEmail.isNullOrEmpty()) {
            // Profile already exists — save to profiles list and go to profile picker
            val profiles = prefs.getString(com.mts.mtsflix.license.ProfileSwitchActivity.KEY_PROFILES, "")
            val list = if (profiles.isNullOrBlank()) mutableListOf() else profiles.split(com.mts.mtsflix.license.ProfileSwitchActivity.SEPARATOR).toMutableList()
            if (!list.contains(savedGoogleEmail)) {
                list.add(savedGoogleEmail)
                prefs.edit().putString(com.mts.mtsflix.license.ProfileSwitchActivity.KEY_PROFILES, list.joinToString(com.mts.mtsflix.license.ProfileSwitchActivity.SEPARATOR)).commit()
            }
            tvStatus.text = "✅ Akaun Google Terhubung!"
            tvStatus.setTextColor(Color.parseColor("#4CAF50"))
            btnGoogleSignIn.visibility = View.GONE
            btnSkipGoogle.visibility = View.GONE
            lifecycleScope.launch(Dispatchers.Main) {
                delay(800)
                launchMainApp()
            }
        } else {
            showGoogleSignInPrompt(username)
        }
    }

    private fun showGoogleSignInPrompt(username: String) {
        progressBar.visibility = View.GONE
        btnVerify.visibility = View.GONE
        btnContact.visibility = View.GONE
        btnCopy.visibility = View.GONE

        tvStatus.text = "🌐 Log Masuk Akaun Google"
        tvStatus.setTextColor(Color.parseColor("#4285F4"))

        tvMessage.visibility = View.VISIBLE
        tvMessage.text = "Sila log masuk dengan Google untuk menyimpan sejarah tontonan & senarai kegemaran supaya rekod tidak hilang sekiranya peranti di-clear data atau uninstall."
        tvMessage.setTextColor(Color.parseColor("#CCCCCC"))

        btnGoogleSignIn.visibility = View.VISIBLE
        btnSkipGoogle.visibility = View.VISIBLE

        // Show QR Pairing button (especially useful for Android TV / Google TV)
        if (btnQrPairing.parent == null) {
            val cardView = btnGoogleSignIn.parent as? android.view.ViewGroup
            cardView?.addView(btnQrPairing)
        }
        btnQrPairing.visibility = View.VISIBLE
    }

    private fun triggerGoogleAccountPicker() {
        try {
            val intent = AccountManager.newChooseAccountIntent(
                null, null, arrayOf("com.google"), false, null, null, null, null
            )
            startActivityForResult(intent, REQUEST_CODE_GOOGLE_PICKER)
        } catch (e: Exception) {
            promptManualEmailInput()
        }
    }

    private fun promptManualEmailInput() {
        val input = EditText(this).apply {
            hint = "contoh@gmail.com"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        android.app.AlertDialog.Builder(this, com.lagradost.cloudstream3.R.style.AlertDialogCustom)
            .setTitle("Log Masuk Akaun Google")
            .setMessage("Masukkan email Google anda untuk menyelaraskan sejarah tontonan:")
            .setView(input)
            .setPositiveButton("Simpan & Teruskan") { _, _ ->
                val email = input.text.toString().trim()
                if (email.isNotEmpty()) {
                    saveGoogleAccount(email)
                } else {
                    launchMainApp()
                }
            }
            .setNegativeButton("Langkah Ini") { _, _ -> launchMainApp() }
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_GOOGLE_PICKER && resultCode == RESULT_OK && data != null) {
            val accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!accountName.isNullOrEmpty()) {
                saveGoogleAccount(accountName)
            } else {
                launchMainApp()
            }
        }
    }

    private fun saveGoogleAccount(email: String) {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().putString("GOOGLE_ACCOUNT_EMAIL", email).commit()

        // Add to profiles list
        val profiles = prefs.getString(com.mts.mtsflix.license.ProfileSwitchActivity.KEY_PROFILES, "")
        val list = if (profiles.isNullOrBlank()) mutableListOf() else profiles.split(com.mts.mtsflix.license.ProfileSwitchActivity.SEPARATOR).toMutableList()
        if (!list.contains(email)) {
            list.add(email)
            prefs.edit().putString(com.mts.mtsflix.license.ProfileSwitchActivity.KEY_PROFILES, list.joinToString(com.mts.mtsflix.license.ProfileSwitchActivity.SEPARATOR)).commit()
        }

        tvStatus.text = "🔄 Memulihkan Sejarah Tontonan..."
        tvStatus.setTextColor(Color.parseColor("#FFA500"))

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                com.mts.mtsflix.cloud.MTSFlixCloudSync.restoreWatchHistory(this@LicenseCheckActivity, email)
            } catch (e: Exception) {}

            withContext(Dispatchers.Main) {
                Toast.makeText(this@LicenseCheckActivity, "Akaun Google ($email) Berjaya Log Masuk! ✅", Toast.LENGTH_LONG).show()
                tvStatus.text = "✅ Log Masuk Google Berjaya!"
                tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                tvMessage.text = "Akaun: $email\nProfil disimpan. Pilih profil untuk teruskan."
                btnGoogleSignIn.visibility = View.GONE
                btnSkipGoogle.visibility = View.GONE
                delay(1200)
                launchMainApp()
            }
        }
    }

    private fun markSetupComplete() {
        val key = "HAS_DONE_SETUP"
        // Save as String in multiple SharedPreferences namespaces (used by MTSFlixInit)
        try {
            getSharedPreferences("rebuild_preference", Context.MODE_PRIVATE)
                .edit().putString(key, "true").apply()
        } catch (e: Exception) { android.util.Log.w("MTSFlix", "setup bypass fail: ${e.message}") }
        try {
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                .edit().putString(key, "true").apply()
        } catch (e: Exception) { android.util.Log.w("MTSFlix", "setup bypass2 fail: ${e.message}") }
        // Save as Boolean using CloudStream's setKey (required for getKey(HAS_DONE_SETUP_KEY, false) to return true)
        try {
            com.lagradost.cloudstream3.CloudStreamApp.setKey(key, true)
        } catch (e: Exception) { android.util.Log.w("MTSFlix", "CloudStreamApp setKey fail: ${e.message}") }
    }

    // ─── UI State Management ──────────────────────────────────────────────────

    private fun setLoadingState(message: String) {
        progressBar.visibility = View.VISIBLE
        btnVerify.visibility = View.VISIBLE
        btnVerify.isEnabled = false
        btnVerify.text = "Menyemak lesen..."
        btnContact.visibility = View.GONE
        btnGoogleSignIn.visibility = View.GONE
        btnSkipGoogle.visibility = View.GONE
        tvStatus.text = message
        tvStatus.setTextColor(Color.parseColor("#FFA500"))
        tvMessage.visibility = View.GONE
        tvUsername.visibility = View.GONE
        tvExpiry.visibility = View.GONE
        btnCopy.visibility = View.VISIBLE
    }

    private fun showVerifiedState(username: String, expiryDate: String) {
        progressBar.visibility = View.GONE
        btnVerify.visibility = View.GONE
        btnContact.visibility = View.GONE
        tvStatus.text = "✅ Lesen Disahkan!"
        tvStatus.setTextColor(Color.parseColor("#4CAF50"))
        tvMessage.visibility = View.VISIBLE
        tvMessage.text = "Selamat datang ke MTSFlix, $username!"
        tvMessage.setTextColor(Color.parseColor("#CCCCCC"))
        tvUsername.visibility = View.VISIBLE
        tvUsername.text = "👤  $username"
        tvExpiry.visibility = View.GONE
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
        btnGoogleSignIn.visibility = View.GONE
        btnSkipGoogle.visibility = View.GONE

        if (showContact) {
            btnContact.visibility = View.VISIBLE
        } else {
            btnContact.visibility = View.GONE
        }
    }

    // ─── Build UI Programmatically ────────────────────────────────────────────

    private fun buildUI() {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.parseColor("#0D0D0D"))
        setContentView(scroll)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(60), dp(24), dp(40))
        }
        scroll.addView(root)

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

        val divider = View(this).apply {
            setBackgroundColor(Color.parseColor("#2A2A2A"))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            lp.bottomMargin = dp(20)
            layoutParams = lp
        }
        cardView.addView(divider)

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

        progressBar = ProgressBar(this).apply {
            isIndeterminate = true
            visibility = View.VISIBLE
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32))
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.topMargin = dp(8)
            layoutParams = lp
        }
        cardView.addView(progressBar)

        btnVerify = Button(this).apply {
            text = "🔑 Semak Lesen"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E50914"))
                cornerRadius = dp(12).toFloat()
            }
            isEnabled = false
            isFocusable = true
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(16)
            layoutParams = lp
            setOnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.04f else 1f).scaleY(if (hasFocus) 1.04f else 1f).setDuration(120).start()
                v.background = GradientDrawable().apply {
                    setColor(Color.parseColor("#E50914"))
                    cornerRadius = dp(12).toFloat()
                    if (hasFocus) setStroke(dp(3), Color.WHITE)
                }
            }
            setOnClickListener {
                val code = DeviceCodeManager.getDeviceCode(this@LicenseCheckActivity)
                startVerification(code)
            }
        }
        cardView.addView(btnVerify)

        btnGoogleSignIn = Button(this).apply {
            text = "🌐 Log Masuk Akaun Google"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#4285F4"))
                cornerRadius = dp(12).toFloat()
            }
            visibility = View.GONE
            isFocusable = true
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(16)
            layoutParams = lp
            setOnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.04f else 1f).scaleY(if (hasFocus) 1.04f else 1f).setDuration(120).start()
                v.background = GradientDrawable().apply {
                    setColor(Color.parseColor("#4285F4"))
                    cornerRadius = dp(12).toFloat()
                    if (hasFocus) setStroke(dp(3), Color.WHITE)
                }
            }
            setOnClickListener { triggerGoogleAccountPicker() }
        }
        cardView.addView(btnGoogleSignIn)

        btnSkipGoogle = Button(this).apply {
            text = "Teruskan ke MTSFlix ➔"
            textSize = 14f
            setTextColor(Color.parseColor("#888888"))
            background = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
            visibility = View.GONE
            isFocusable = true
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(8)
            layoutParams = lp
            setOnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.04f else 1f).scaleY(if (hasFocus) 1.04f else 1f).setDuration(120).start()
                (v as Button).setTextColor(if (hasFocus) Color.WHITE else Color.parseColor("#888888"))
            }
            setOnClickListener { launchMainApp() }
        }
        cardView.addView(btnSkipGoogle)

        btnQrPairing = Button(this).apply {
            text = "📺 Log Masuk via QR Code (Android TV)"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1a3a1a"))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#2e7d32"))
            }
            visibility = View.GONE
            isFocusable = true
            setPadding(dp(16), dp(12), dp(16), dp(12))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(10)
            layoutParams = lp
            setOnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.04f else 1f).scaleY(if (hasFocus) 1.04f else 1f).setDuration(120).start()
                v.background = GradientDrawable().apply {
                    setColor(if (hasFocus) Color.parseColor("#2e7d32") else Color.parseColor("#1a3a1a"))
                    cornerRadius = dp(12).toFloat()
                    if (hasFocus) setStroke(dp(2), Color.WHITE)
                    else setStroke(dp(1), Color.parseColor("#2e7d32"))
                }
            }
            setOnClickListener {
                startActivity(Intent(this@LicenseCheckActivity, com.mts.mtsflix.license.TVPairingActivity::class.java))
            }
        }
        // btnQrPairing will be added to cardView dynamically in showGoogleSignInPrompt()

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
