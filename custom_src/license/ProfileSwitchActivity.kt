package com.mts.mtsflix.license

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MTSFlix Profile / Switch Account Screen
 * - Fully compatible with Android TV / Google TV (D-pad remote navigation)
 * - Visual focus indicators so user knows which button is selected
 * - Supports multiple profiles per device
 */
class ProfileSwitchActivity : AppCompatActivity() {

    companion object {
        const val KEY_PROFILES = "MTSFLIX_SAVED_PROFILES"
        const val KEY_ACTIVE_EMAIL = "GOOGLE_ACCOUNT_EMAIL"
        const val SEPARATOR = "|||"
        const val REQUEST_GOOGLE_ACCOUNT = 9010
    }

    private lateinit var container: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvTitle: TextView
    private var isTV = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isTV = isAndroidTV()

        window.statusBarColor = Color.parseColor("#0A0A0F")
        window.navigationBarColor = Color.parseColor("#0A0A0F")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A0F"))
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, -1)
            isFocusable = false
            descendantFocusability = LinearLayout.FOCUS_AFTER_DESCENDANTS
        }

        val topPad = if (isTV) 60 else 80
        val sidePad = if (isTV) 120 else 48

        // ── Header ────────────────────────────────────────────────────────────
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(sidePad, topPad, sidePad, 24)
        }

        val tvLogo = TextView(this).apply {
            text = "MTS"
            textSize = if (isTV) 52f else 42f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E50914"))
            gravity = Gravity.CENTER
        }
        val tvLogoSub = TextView(this).apply {
            text = "FLIX"
            textSize = if (isTV) 22f else 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            letterSpacing = 0.4f
        }
        tvTitle = TextView(this).apply {
            text = "Pilih Profil"
            textSize = if (isTV) 28f else 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 8)
        }
        val tvSubtitle = TextView(this).apply {
            text = if (isTV) "Gunakan ▲▼ untuk navigasi, OK untuk pilih" else "Siapa yang menonton sekarang?"
            textSize = if (isTV) 16f else 14f
            setTextColor(Color.parseColor("#AAAAAA"))
            gravity = Gravity.CENTER
        }

        header.addView(tvLogo)
        header.addView(tvLogoSub)
        header.addView(tvTitle)
        header.addView(tvSubtitle)

        // ── Profile list ──────────────────────────────────────────────────────
        scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            isFocusable = false
        }
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(sidePad, 24, sidePad, 24)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        scrollView.addView(container)

        // ── Bottom ────────────────────────────────────────────────────────────
        progressBar = ProgressBar(this).apply { visibility = View.GONE }
        val btnAddProfile = createAddProfileButton()
        val bottomLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(sidePad, 16, sidePad, if (isTV) 60 else 48)
        }
        bottomLayout.addView(progressBar)
        bottomLayout.addView(btnAddProfile)

        root.addView(header)
        root.addView(scrollView)
        root.addView(bottomLayout)
        setContentView(root)

        loadProfiles()
    }

    private fun isAndroidTV(): Boolean {
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
        return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    private fun loadProfiles() {
        container.removeAllViews()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val profilesStr = prefs.getString(KEY_PROFILES, "")
        val profiles = if (profilesStr.isNullOrBlank()) emptyList()
                       else profilesStr.split(SEPARATOR).filter { it.isNotBlank() }
        val activeEmail = prefs.getString(KEY_ACTIVE_EMAIL, null)

        if (profiles.isEmpty()) {
            val tvEmpty = TextView(this).apply {
                text = "Belum ada profil.\nTekan butang di bawah untuk tambah profil Google."
                textSize = if (isTV) 18f else 14f
                setTextColor(Color.parseColor("#AAAAAA"))
                gravity = Gravity.CENTER
                setPadding(0, 32, 0, 32)
            }
            container.addView(tvEmpty)
        } else {
            for (email in profiles) {
                container.addView(createProfileCard(email, email == activeEmail))
            }
        }

        // Auto-focus first profile card or Add button after load
        container.post {
            val firstFocusable = container.getChildAt(0) ?: null
            firstFocusable?.requestFocus() ?: run {
                // focus Add Profile button
            }
        }
    }

    private fun createProfileCard(email: String, isActive: Boolean): View {
        val cardSize = if (isTV) 90 else 70

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(if (isTV) 32 else 24, if (isTV) 24 else 18, if (isTV) 32 else 24, if (isTV) 24 else 18)
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.setMargins(0, if (isTV) 16 else 10, 0, 0)
            layoutParams = lp
            isFocusable = true
            isFocusableInTouchMode = false
            isClickable = true
            background = buildCardBackground(isActive, false)
        }

        // Focus highlight for TV remote
        card.setOnFocusChangeListener { v, hasFocus ->
            v.background = buildCardBackground(isActive, hasFocus)
            if (hasFocus) {
                v.animate().scaleX(if (isTV) 1.04f else 1.02f).scaleY(if (isTV) 1.04f else 1.02f).setDuration(120).start()
                // scroll into view
                scrollView.post { scrollView.smoothScrollTo(0, v.top) }
            } else {
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
        }

        // Avatar
        val avatar = TextView(this).apply {
            text = email.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            textSize = if (isTV) 24f else 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            val colors = intArrayOf(Color.parseColor("#E50914"), Color.parseColor("#B81D24"))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
                shape = GradientDrawable.OVAL
            }
            val lp = LinearLayout.LayoutParams(cardSize, cardSize)
            lp.setMargins(0, 0, if (isTV) 28 else 18, 0)
            layoutParams = lp
        }

        // Email + active badge
        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val tvEmail = TextView(this).apply {
            text = email
            textSize = if (isTV) 18f else 14f
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        textContainer.addView(tvEmail)
        if (isActive) {
            val tvActive = TextView(this).apply {
                text = "● Aktif sekarang"
                textSize = if (isTV) 14f else 11f
                setTextColor(Color.parseColor("#4CAF50"))
            }
            textContainer.addView(tvActive)
        }

        // Delete button
        val btnDelete = TextView(this).apply {
            text = "✕"
            textSize = if (isTV) 20f else 16f
            setTextColor(Color.parseColor("#888888"))
            setPadding(20, 12, 8, 12)
            isFocusable = true
            isFocusableInTouchMode = false
            isClickable = true
            setOnFocusChangeListener { v, hasFocus ->
                (v as TextView).setTextColor(if (hasFocus) Color.parseColor("#E50914") else Color.parseColor("#888888"))
                if (hasFocus) v.animate().scaleX(1.2f).scaleY(1.2f).setDuration(100).start()
                else v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }
            setOnClickListener { deleteProfile(email) }
        }

        card.addView(avatar)
        card.addView(textContainer)
        card.addView(btnDelete)

        card.setOnClickListener { switchToProfile(email) }

        // Handle DPAD_CENTER / ENTER key on card
        card.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                switchToProfile(email)
                true
            } else false
        }

        return card
    }

    private fun buildCardBackground(isActive: Boolean, isFocused: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = if (isTV) 20f else 14f
            when {
                isFocused -> {
                    setColor(Color.parseColor("#2A0A0A"))
                    setStroke(if (isTV) 4 else 2, Color.parseColor("#FF1744")) // bright red when focused
                }
                isActive -> {
                    setColor(Color.parseColor("#1A1A2E"))
                    setStroke(2, Color.parseColor("#E50914"))
                }
                else -> {
                    setColor(Color.parseColor("#141414"))
                    setStroke(1, Color.parseColor("#2A2A2A"))
                }
            }
        }
    }

    private fun createAddProfileButton(): View {
        val btn = TextView(this).apply {
            text = "+ Tambah Profil Google"
            textSize = if (isTV) 18f else 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(if (isTV) 64 else 48, if (isTV) 26 else 20, if (isTV) 64 else 48, if (isTV) 26 else 20)
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.setMargins(0, 20, 0, 0)
            layoutParams = lp
            isFocusable = true
            isFocusableInTouchMode = false
            isClickable = true
            background = buildButtonBackground(false)
            setOnFocusChangeListener { v, hasFocus ->
                v.background = buildButtonBackground(hasFocus)
                if (hasFocus) {
                    v.animate().scaleX(if (isTV) 1.04f else 1.02f).scaleY(if (isTV) 1.04f else 1.02f).setDuration(120).start()
                } else {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }
            }
            setOnClickListener { promptAddGoogle() }
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                    promptAddGoogle(); true
                } else false
            }
        }
        return btn
    }

    private fun buildButtonBackground(isFocused: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = if (isTV) 16f else 12f
            if (isFocused) {
                setColor(Color.parseColor("#E50914"))
                setStroke(0, Color.TRANSPARENT)
            } else {
                setColor(Color.parseColor("#1A1A1A"))
                setStroke(1, Color.parseColor("#E50914"))
            }
        }
    }

    private fun promptAddGoogle() {
        try {
            val intent = android.accounts.AccountManager.newChooseAccountIntent(
                null, null, arrayOf("com.google"), null, null, null, null
            )
            startActivityForResult(intent, REQUEST_GOOGLE_ACCOUNT)
        } catch (e: Exception) {
            promptManualEmail()
        }
    }

    private fun promptManualEmail() {
        val input = EditText(this).apply {
            hint = "contoh@gmail.com"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or
                        android.text.InputType.TYPE_CLASS_TEXT
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setPadding(24, 16, 24, 16)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Tambah Profil Google")
            .setMessage("Masukkan alamat Gmail:")
            .setView(input)
            .setPositiveButton("Tambah") { _, _ ->
                val email = input.text.toString().trim()
                if (email.contains("@")) addProfile(email)
                else Toast.makeText(this, "Email tidak sah", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_GOOGLE_ACCOUNT && resultCode == RESULT_OK && data != null) {
            val email = data.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
            if (!email.isNullOrEmpty()) addProfile(email)
        }
    }

    private fun addProfile(email: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val current = prefs.getString(KEY_PROFILES, "")
        val list = if (current.isNullOrBlank()) mutableListOf()
                   else current.split(SEPARATOR).filter { it.isNotBlank() }.toMutableList()
        if (!list.contains(email)) {
            list.add(email)
            prefs.edit().putString(KEY_PROFILES, list.joinToString(SEPARATOR)).commit()
        }
        switchToProfile(email)
    }

    private fun deleteProfile(email: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Padam Profil")
            .setMessage("Padam profil $email?\nData tontonan di cloud tidak akan dipadam.")
            .setPositiveButton("Padam") { _, _ ->
                val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                val current = prefs.getString(KEY_PROFILES, "")
                val list = if (current.isNullOrBlank()) mutableListOf()
                           else current.split(SEPARATOR).filter { it.isNotBlank() }.toMutableList()
                list.remove(email)
                val activeEmail = prefs.getString(KEY_ACTIVE_EMAIL, null)
                val editor = prefs.edit().putString(KEY_PROFILES, list.joinToString(SEPARATOR))
                if (activeEmail == email) editor.remove(KEY_ACTIVE_EMAIL)
                editor.commit()
                loadProfiles()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun switchToProfile(email: String) {
        if (progressBar.visibility == View.VISIBLE) return // Prevent double tap
        progressBar.visibility = View.VISIBLE
        tvTitle.text = "Memuat profil..."
        // Disable all focusable items during load
        container.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                com.mts.mtsflix.cloud.MTSFlixCloudSync.restoreWatchHistory(this@ProfileSwitchActivity, email)
            } catch (e: Exception) {}

            val prefs = PreferenceManager.getDefaultSharedPreferences(this@ProfileSwitchActivity)
            prefs.edit().putString(KEY_ACTIVE_EMAIL, email).commit()

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                val intent = Intent(this@ProfileSwitchActivity,
                    Class.forName("com.lagradost.cloudstream3.MainActivity"))
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                finish()
            }
        }
    }

    // Allow back button to not go back (no activity to go back to)
    override fun onBackPressed() {
        // Do nothing — user must select a profile
    }
}
