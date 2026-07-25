package com.mts.mtsflix.license

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
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
 * Shown after license verified — user picks which Google account/profile to use.
 * Supports multiple profiles per device.
 */
class ProfileSwitchActivity : AppCompatActivity() {

    companion object {
        const val KEY_PROFILES = "MTSFLIX_SAVED_PROFILES"    // JSON array of emails
        const val KEY_ACTIVE_EMAIL = "GOOGLE_ACCOUNT_EMAIL"  // active account email
        const val SEPARATOR = "|||"
        const val REQUEST_GOOGLE_ACCOUNT = 9010
    }

    private lateinit var container: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvTitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full screen dark background
        window.statusBarColor = Color.parseColor("#0A0A0F")
        window.navigationBarColor = Color.parseColor("#0A0A0F")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A0F"))
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, -1)
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 80, 48, 24)
        }

        // App Logo Text
        val tvLogo = TextView(this).apply {
            text = "MTS"
            textSize = 42f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E50914"))
            gravity = Gravity.CENTER
        }
        val tvLogoSub = TextView(this).apply {
            text = "FLIX"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            letterSpacing = 0.4f
        }

        tvTitle = TextView(this).apply {
            text = "Pilih Profil"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 8)
        }

        val tvSubtitle = TextView(this).apply {
            text = "Siapa yang menonton sekarang?"
            textSize = 14f
            setTextColor(Color.parseColor("#AAAAAA"))
            gravity = Gravity.CENTER
        }

        header.addView(tvLogo)
        header.addView(tvLogoSub)
        header.addView(tvTitle)
        header.addView(tvSubtitle)

        // Scrollable profile list
        scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }

        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        scrollView.addView(container)

        // Progress bar
        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
        }

        // Add Profile Button
        val btnAddProfile = createAddProfileButton()

        // Bottom area
        val bottomLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 16, 48, 48)
        }
        bottomLayout.addView(progressBar)
        bottomLayout.addView(btnAddProfile)

        root.addView(header)
        root.addView(scrollView)
        root.addView(bottomLayout)
        setContentView(root)

        loadProfiles()
    }

    private fun loadProfiles() {
        container.removeAllViews()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val profilesStr = prefs.getString(KEY_PROFILES, "")
        val profiles = if (profilesStr.isNullOrBlank()) emptyList()
                       else profilesStr.split(SEPARATOR).filter { it.isNotBlank() }
        val activeEmail = prefs.getString(KEY_ACTIVE_EMAIL, null)

        if (profiles.isEmpty()) {
            // No profiles yet — go straight to Google sign-in
            val tvEmpty = TextView(this).apply {
                text = "Belum ada profil.\nTambah profil Google untuk mula."
                textSize = 14f
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
    }

    private fun createProfileCard(email: String, isActive: Boolean): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 20, 24, 20)
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.setMargins(0, 12, 0, 0)
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f
                setColor(if (isActive) Color.parseColor("#1A1A2E") else Color.parseColor("#141414"))
                if (isActive) setStroke(2, Color.parseColor("#E50914"))
                else setStroke(1, Color.parseColor("#2A2A2A"))
            }
            isClickable = true
            isFocusable = true
        }

        // Avatar circle
        val avatar = TextView(this).apply {
            text = email.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            val colors = intArrayOf(Color.parseColor("#E50914"), Color.parseColor("#B81D24"))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
                shape = GradientDrawable.OVAL
            }
            val lp = LinearLayout.LayoutParams(80, 80)
            lp.setMargins(0, 0, 20, 0)
            layoutParams = lp
        }

        // Email & active badge
        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val tvEmail = TextView(this).apply {
            text = email
            textSize = 13f
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        if (isActive) {
            val tvActive = TextView(this).apply {
                text = "● Aktif sekarang"
                textSize = 11f
                setTextColor(Color.parseColor("#4CAF50"))
            }
            textContainer.addView(tvEmail)
            textContainer.addView(tvActive)
        } else {
            textContainer.addView(tvEmail)
        }

        // Delete button
        val btnDelete = TextView(this).apply {
            text = "✕"
            textSize = 16f
            setTextColor(Color.parseColor("#666666"))
            setPadding(16, 8, 8, 8)
            isClickable = true
            isFocusable = true
        }

        card.addView(avatar)
        card.addView(textContainer)
        card.addView(btnDelete)

        // Click card → switch to this profile
        card.setOnClickListener { switchToProfile(email) }

        // Click delete → remove profile
        btnDelete.setOnClickListener { deleteProfile(email) }

        return card
    }

    private fun createAddProfileButton(): View {
        return TextView(this).apply {
            text = "+ Tambah Profil Google"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(48, 20, 48, 20)
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.setMargins(0, 16, 0, 0)
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f
                setColor(Color.parseColor("#1A1A1A"))
                setStroke(1, Color.parseColor("#E50914"))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { promptAddGoogle() }
        }
    }

    private fun promptAddGoogle() {
        try {
            val intent = android.accounts.AccountManager.get(this).newChooseAccountIntent(
                null, null, arrayOf("com.google"), null, null, null, null
            )
            startActivityForResult(intent, REQUEST_GOOGLE_ACCOUNT)
        } catch (e: Exception) {
            promptManualEmail()
        }
    }

    private fun promptManualEmail() {
        val input = android.widget.EditText(this).apply {
            hint = "contoh@gmail.com"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Tambah Profil Google")
            .setMessage("Masukkan alamat Gmail:")
            .setView(input)
            .setPositiveButton("Tambah") { _, _ ->
                val email = input.text.toString().trim()
                if (email.contains("@")) addProfile(email)
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
        val list = if (current.isNullOrBlank()) mutableListOf() else current.split(SEPARATOR).toMutableList()
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
                val list = if (current.isNullOrBlank()) mutableListOf() else current.split(SEPARATOR).toMutableList()
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
        progressBar.visibility = View.VISIBLE
        tvTitle.text = "Memuat profil..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Restore this profile's cloud history
                com.mts.mtsflix.cloud.MTSFlixCloudSync.restoreWatchHistory(this@ProfileSwitchActivity, email)
            } catch (e: Exception) {}

            // Save as active profile
            val prefs = PreferenceManager.getDefaultSharedPreferences(this@ProfileSwitchActivity)
            prefs.edit().putString(KEY_ACTIVE_EMAIL, email).commit()

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                // Launch main app
                val intent = Intent(this@ProfileSwitchActivity, Class.forName("com.lagradost.cloudstream3.MainActivity"))
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                finish()
            }
        }
    }
}
