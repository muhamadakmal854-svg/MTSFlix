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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MTSFlix Profile / Switch Account Screen v3.0
 *
 * Fix v3.0:
 * - WindowInsets handling: button no longer hidden behind navigation bar
 * - Layout restructured: everything in one ScrollView (no floating bottom button)
 * - TV: compact header, no wasted vertical space
 * - Phone: proper bottom padding for gesture/button nav bar
 */
class ProfileSwitchActivity : AppCompatActivity() {

    companion object {
        const val KEY_PROFILES   = "MTSFLIX_SAVED_PROFILES"
        const val KEY_ACTIVE_EMAIL = "GOOGLE_ACCOUNT_EMAIL"
        const val SEPARATOR      = "|||"
        const val REQUEST_GOOGLE_ACCOUNT = 9010
    }

    private lateinit var profileContainer: LinearLayout
    private lateinit var tvTitle: TextView
    private lateinit var progressBar: ProgressBar
    private var isTV = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isTV = isAndroidTV()

        window.statusBarColor     = Color.parseColor("#0A0A0F")
        window.navigationBarColor = Color.parseColor("#0A0A0F")

        // ── Outer scroll: the ENTIRE screen scrolls, nothing is stranded below ──
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0A0A0F"))
            layoutParams = LinearLayout.LayoutParams(-1, -1)
            isFillViewport = true          // stretch content to at least screen height
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }

        val sidePad  = if (isTV) dp(120) else dp(24)
        val topPad   = if (isTV) dp(48)  else dp(60)
        val botPad   = if (isTV) dp(48)  else dp(32)

        // ── Header ────────────────────────────────────────────────────────────
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(sidePad, topPad, sidePad, dp(if (isTV) 16 else 24))
        }

        val tvLogo = TextView(this).apply {
            text = "MTS"; textSize = if (isTV) 44f else 36f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E50914")); gravity = Gravity.CENTER
        }
        val tvLogoSub = TextView(this).apply {
            text = "FLIX"; textSize = if (isTV) 18f else 14f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; letterSpacing = 0.4f
        }
        tvTitle = TextView(this).apply {
            text = "Pilih Profil"; textSize = if (isTV) 26f else 20f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; setPadding(0, dp(20), 0, dp(4))
        }
        val tvSub = TextView(this).apply {
            text = if (isTV) "Gunakan ▲▼ untuk navigasi, OK untuk pilih"
                   else "Siapa yang menonton sekarang?"
            textSize = if (isTV) 14f else 13f
            setTextColor(Color.parseColor("#888888")); gravity = Gravity.CENTER
        }
        header.addView(tvLogo); header.addView(tvLogoSub)
        header.addView(tvTitle); header.addView(tvSub)

        // ── Profile list ──────────────────────────────────────────────────────
        profileContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(sidePad, dp(8), sidePad, dp(8))
        }

        // ── Add profile button ─────────────────────────────────────────────────
        val btnAdd = createAddButton()

        // ── Progress ──────────────────────────────────────────────────────────
        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(-2, dp(36))
            lp.gravity = Gravity.CENTER_HORIZONTAL; lp.topMargin = dp(8)
            layoutParams = lp
        }

        val bottomLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(sidePad, dp(8), sidePad, botPad)
        }
        bottomLayout.addView(progressBar)
        bottomLayout.addView(btnAdd)

        // ── Assemble ──────────────────────────────────────────────────────────
        root.addView(header)
        root.addView(profileContainer)
        root.addView(bottomLayout)
        scrollView.addView(root)
        setContentView(scrollView)

        // ── Handle navigation bar insets (CRITICAL for button not being cut off)
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        loadProfiles()
    }

    private fun isAndroidTV(): Boolean {
        val mgr = getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
        return mgr.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun loadProfiles() {
        profileContainer.removeAllViews()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val raw   = prefs.getString(KEY_PROFILES, "")
        val list  = if (raw.isNullOrBlank()) emptyList()
                    else raw.split(SEPARATOR).filter { it.isNotBlank() }
        val active = prefs.getString(KEY_ACTIVE_EMAIL, null)

        if (list.isEmpty()) {
            val tv = TextView(this).apply {
                text = "Belum ada profil.\nTekan butang di bawah untuk tambah profil Google."
                textSize = if (isTV) 16f else 13f
                setTextColor(Color.parseColor("#888888")); gravity = Gravity.CENTER
                setPadding(0, dp(24), 0, dp(24))
            }
            profileContainer.addView(tv)
        } else {
            list.forEach { email ->
                profileContainer.addView(createProfileCard(email, email == active))
            }
        }

        // Auto-focus first card on TV
        profileContainer.post { profileContainer.getChildAt(0)?.requestFocus() }
    }

    private fun createProfileCard(email: String, isActive: Boolean): View {
        val avatarSize = if (isTV) dp(80) else dp(60)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.setMargins(0, if (isTV) dp(14) else dp(10), 0, 0)
            layoutParams = lp
            setPadding(dp(if (isTV) 28 else 18), dp(if (isTV) 20 else 16),
                       dp(if (isTV) 28 else 18), dp(if (isTV) 20 else 16))
            isFocusable = true; isFocusableInTouchMode = false; isClickable = true
            background = cardBg(isActive, false)
        }

        card.setOnFocusChangeListener { v, f ->
            v.background = cardBg(isActive, f)
            v.animate().scaleX(if (f && isTV) 1.04f else 1f)
                       .scaleY(if (f && isTV) 1.04f else 1f).setDuration(120).start()
        }

        // Avatar
        val avatar = TextView(this).apply {
            text = email.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            textSize = if (isTV) 22f else 18f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#E50914"), Color.parseColor("#B81D24"))).apply {
                shape = GradientDrawable.OVAL
            }
            val lp = LinearLayout.LayoutParams(avatarSize, avatarSize)
            lp.marginEnd = dp(if (isTV) 24 else 16); layoutParams = lp
        }

        // Text
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val tvEmail = TextView(this).apply {
            text = email; textSize = if (isTV) 17f else 14f; setTextColor(Color.WHITE)
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        texts.addView(tvEmail)
        if (isActive) {
            texts.addView(TextView(this).apply {
                text = "● Aktif sekarang"; textSize = if (isTV) 13f else 11f
                setTextColor(Color.parseColor("#4CAF50"))
            })
        }

        // Delete button
        val btnDel = TextView(this).apply {
            text = "✕"; textSize = if (isTV) 18f else 15f
            setTextColor(Color.parseColor("#666666"))
            setPadding(dp(16), dp(8), dp(4), dp(8))
            isFocusable = true; isFocusableInTouchMode = false; isClickable = true
            setOnFocusChangeListener { v, f ->
                (v as TextView).setTextColor(if (f) Color.parseColor("#E50914") else Color.parseColor("#666666"))
                v.animate().scaleX(if (f) 1.2f else 1f).scaleY(if (f) 1.2f else 1f).setDuration(100).start()
            }
            setOnClickListener { confirmDelete(email) }
        }

        card.addView(avatar); card.addView(texts); card.addView(btnDel)
        card.setOnClickListener { switchToProfile(email) }
        card.setOnKeyListener { _, k, e ->
            if (e.action == KeyEvent.ACTION_DOWN &&
                (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER)) {
                switchToProfile(email); true
            } else false
        }
        return card
    }

    private fun cardBg(active: Boolean, focused: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = if (isTV) dp(16).toFloat() else dp(14).toFloat()
        when {
            focused -> { setColor(Color.parseColor("#2A0A0A")); setStroke(if (isTV) dp(3) else dp(2), Color.parseColor("#FF1744")) }
            active  -> { setColor(Color.parseColor("#1A1A2E")); setStroke(dp(2), Color.parseColor("#E50914")) }
            else    -> { setColor(Color.parseColor("#141414")); setStroke(dp(1), Color.parseColor("#2A2A2A")) }
        }
    }

    private fun createAddButton(): View {
        return TextView(this).apply {
            text = "+ Tambah Profil Google"
            textSize = if (isTV) 17f else 14f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(dp(if (isTV) 56 else 32), dp(if (isTV) 22 else 16),
                       dp(if (isTV) 56 else 32), dp(if (isTV) 22 else 16))
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.topMargin = dp(12); layoutParams = lp
            isFocusable = true; isFocusableInTouchMode = false; isClickable = true
            background = btnBg(false)
            setOnFocusChangeListener { v, f ->
                v.background = btnBg(f)
                v.animate().scaleX(if (f && isTV) 1.04f else 1f)
                           .scaleY(if (f && isTV) 1.04f else 1f).setDuration(120).start()
            }
            setOnClickListener { promptAddGoogle() }
            setOnKeyListener { _, k, e ->
                if (e.action == KeyEvent.ACTION_DOWN &&
                    (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER)) {
                    promptAddGoogle(); true
                } else false
            }
        }
    }

    private fun btnBg(focused: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = if (isTV) dp(14).toFloat() else dp(12).toFloat()
        if (focused) { setColor(Color.parseColor("#E50914")); setStroke(0, Color.TRANSPARENT) }
        else         { setColor(Color.parseColor("#1A1A1A")); setStroke(dp(1), Color.parseColor("#E50914")) }
    }

    private fun promptAddGoogle() {
        try {
            val intent = android.accounts.AccountManager.newChooseAccountIntent(
                null, null, arrayOf("com.google"), null, null, null, null)
            startActivityForResult(intent, REQUEST_GOOGLE_ACCOUNT)
        } catch (e: Exception) { promptManualEmail() }
    }

    private fun promptManualEmail() {
        val input = android.widget.EditText(this).apply {
            hint = "contoh@gmail.com"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or
                        android.text.InputType.TYPE_CLASS_TEXT
            setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            setPadding(dp(24), dp(16), dp(24), dp(16))
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Tambah Profil Google").setMessage("Masukkan alamat Gmail:")
            .setView(input)
            .setPositiveButton("Tambah") { _, _ ->
                val email = input.text.toString().trim()
                if (email.contains("@")) addProfile(email)
                else Toast.makeText(this, "Email tidak sah", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null).show()
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
        val cur   = prefs.getString(KEY_PROFILES, "")
        val list  = if (cur.isNullOrBlank()) mutableListOf()
                    else cur.split(SEPARATOR).filter { it.isNotBlank() }.toMutableList()
        if (!list.contains(email)) {
            list.add(email)
            prefs.edit().putString(KEY_PROFILES, list.joinToString(SEPARATOR)).commit()
        }
        switchToProfile(email)
    }

    private fun confirmDelete(email: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Padam Profil")
            .setMessage("Padam profil $email?\nData di cloud tidak akan dipadam.")
            .setPositiveButton("Padam") { _, _ ->
                val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                val cur   = prefs.getString(KEY_PROFILES, "")
                val list  = if (cur.isNullOrBlank()) mutableListOf()
                            else cur.split(SEPARATOR).filter { it.isNotBlank() }.toMutableList()
                list.remove(email)
                val ed = prefs.edit().putString(KEY_PROFILES, list.joinToString(SEPARATOR))
                if (prefs.getString(KEY_ACTIVE_EMAIL, null) == email) ed.remove(KEY_ACTIVE_EMAIL)
                ed.commit(); loadProfiles()
            }
            .setNegativeButton("Batal", null).show()
    }

    private fun switchToProfile(email: String) {
        if (progressBar.visibility == View.VISIBLE) return
        progressBar.visibility = View.VISIBLE
        tvTitle.text = "Memuat profil..."
        profileContainer.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try { com.mts.mtsflix.cloud.MTSFlixCloudSync.restoreWatchHistory(
                    this@ProfileSwitchActivity, email) } catch (e: Exception) {}
            PreferenceManager.getDefaultSharedPreferences(this@ProfileSwitchActivity)
                .edit().putString(KEY_ACTIVE_EMAIL, email).commit()
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                val intent = Intent(this@ProfileSwitchActivity,
                    Class.forName("com.lagradost.cloudstream3.MainActivity"))
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent); finish()
            }
        }
    }

    // Block back button — user must select a profile
    override fun onBackPressed() {}
}
