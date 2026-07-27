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
 * MTSFlix Netflix-Style Profile Picker
 * 1 email → sehingga 5 profil
 * Grid 2-kolum (phone) / 3-kolum (TV)
 */
class ProfilePickerActivity : AppCompatActivity() {

    private var isTV = false
    private lateinit var gridContainer: LinearLayout
    private lateinit var tvTitle: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isTV = isAndroidTV()
        window.statusBarColor     = Color.parseColor("#141414")
        window.navigationBarColor = Color.parseColor("#141414")
        buildUI()
        loadProfiles()
    }

    private fun isAndroidTV(): Boolean {
        val mgr = getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
        return mgr.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ── Build UI ──────────────────────────────────────────────────────────────

    private fun buildUI() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#141414"))
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(if (isTV) 80 else 24), dp(if (isTV) 48 else 56), dp(if (isTV) 80 else 24), dp(32))
        }

        // Logo
        root.addView(TextView(this).apply {
            text = "MTS"; textSize = if (isTV) 36f else 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E50914")); gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "FLIX"; textSize = if (isTV) 14f else 11f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; letterSpacing = 0.5f; setPadding(0, 0, 0, dp(20))
        })

        tvTitle = TextView(this).apply {
            text = "Siapa yang menonton?"; textSize = if (isTV) 26f else 20f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(if (isTV) 40 else 28))
        }
        root.addView(tvTitle)

        // Grid container
        gridContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        root.addView(gridContainer)

        // Progress
        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(dp(40), dp(40))
            lp.topMargin = dp(12); lp.gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp
        }
        root.addView(progressBar)

        // Bottom buttons row
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(if (isTV) 40 else 28), 0, 0)
        }
        val btnManage = buildBottomBtn("✏️ Urus Profil") { launchManage() }
        btnRow.addView(btnManage)
        root.addView(btnRow)

        // TV navigation hint
        if (isTV) {
            root.addView(TextView(this).apply {
                text = "▲▼◀▶ navigasi  •  OK pilih  •  ← kembali"
                textSize = 12f; setTextColor(Color.parseColor("#555555"))
                gravity = Gravity.CENTER; setPadding(0, dp(16), 0, 0)
            })
        }

        scroll.addView(root)
        setContentView(scroll)

        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun buildBottomBtn(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label; textSize = if (isTV) 15f else 13f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#AAAAAA")); gravity = Gravity.CENTER
        setPadding(dp(24), dp(14), dp(24), dp(14))
        isFocusable = true; isFocusableInTouchMode = false; isClickable = true
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = dp(8).toFloat()
            setColor(Color.parseColor("#222222")); setStroke(dp(1), Color.parseColor("#333333"))
        }
        val lp = LinearLayout.LayoutParams(-2, -2); lp.setMargins(dp(8), 0, dp(8), 0); layoutParams = lp
        setOnFocusChangeListener { v, f ->
            (v as TextView).setTextColor(if (f) Color.WHITE else Color.parseColor("#AAAAAA"))
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(8).toFloat()
                setColor(if (f) Color.parseColor("#333333") else Color.parseColor("#222222"))
                setStroke(if (f) dp(2) else dp(1), if (f) Color.WHITE else Color.parseColor("#333333"))
            }
            v.animate().scaleX(if (f && isTV) 1.05f else 1f).scaleY(if (f && isTV) 1.05f else 1f).setDuration(120).start()
        }
        setOnClickListener { onClick() }
    }

    // ── Load profiles into grid ───────────────────────────────────────────────

    private fun loadProfiles() {
        gridContainer.removeAllViews()
        val profiles = ProfileManager.loadProfiles(this)
        val active   = ProfileManager.getActiveProfile(this)
        val cols     = if (isTV) 4 else 2
        val avatarSz = if (isTV) dp(120) else dp(88)

        // Build grid rows
        var row: LinearLayout? = null
        profiles.forEachIndexed { i, profile ->
            if (i % cols == 0) {
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                    val lp = LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(if (isTV) 32 else 20); layoutParams = lp
                }
                gridContainer.addView(row)
            }
            row?.addView(buildProfileCard(profile, profile.id == active?.id, avatarSz))
        }

        // Add "+" button if < MAX
        if (profiles.size < ProfileManager.MAX_PROFILES) {
            if (profiles.size % cols == 0) {
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                    val lp = LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(if (isTV) 32 else 20); layoutParams = lp
                }
                gridContainer.addView(row)
            }
            row?.addView(buildAddCard(avatarSz))
        }

        // Show empty state
        if (profiles.isEmpty()) {
            gridContainer.addView(TextView(this).apply {
                text = "Tiada profil lagi.\nTekan \"+\" untuk tambah profil pertama anda."
                textSize = if (isTV) 16f else 13f; setTextColor(Color.parseColor("#666666"))
                gravity = Gravity.CENTER; setPadding(0, dp(20), 0, dp(20))
            })
        }

        // Auto-focus first item on TV
        gridContainer.post {
            val firstRow = gridContainer.getChildAt(0) as? LinearLayout
            firstRow?.getChildAt(0)?.requestFocus()
        }
    }

    private fun buildProfileCard(profile: MtsProfile, isActive: Boolean, avatarSz: Int): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            isFocusable = true; isFocusableInTouchMode = false; isClickable = true
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            lp.setMargins(dp(8), 0, dp(8), 0); layoutParams = lp
        }

        // Avatar circle
        val avatarWrap = FrameLayout(this).apply {
            val lp = LinearLayout.LayoutParams(avatarSz, avatarSz); lp.bottomMargin = dp(10); layoutParams = lp
        }
        val avatar = TextView(this).apply {
            text = profile.avatarLetter
            textSize = (avatarSz / resources.displayMetrics.density * 0.38f)
            typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(profile.avatarColor))
                if (isActive) setStroke(dp(3), Color.WHITE)
            }
        }
        avatarWrap.addView(avatar)

        // Badges (top-right)
        if (profile.pinHash != null) {
            avatarWrap.addView(TextView(this).apply {
                text = "🔒"; textSize = if (isTV) 14f else 11f; gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(Color.parseColor("#1A1A1A"))
                }
                val lp = FrameLayout.LayoutParams(dp(if (isTV) 32 else 24), dp(if (isTV) 32 else 24), Gravity.TOP or Gravity.END)
                layoutParams = lp
            })
        }
        if (profile.isKids) {
            avatarWrap.addView(TextView(this).apply {
                text = "K"; textSize = if (isTV) 10f else 8f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE); gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE; cornerRadius = dp(4).toFloat()
                    setColor(Color.parseColor("#2196F3"))
                }
                val offsetTop = if (profile.pinHash != null) dp(if (isTV) 34 else 26) else 0
                val lp = FrameLayout.LayoutParams(dp(if (isTV) 28 else 20), dp(if (isTV) 18 else 14), Gravity.TOP or Gravity.END)
                lp.topMargin = offsetTop; layoutParams = lp
            })
        }

        card.addView(avatarWrap)

        // Name
        card.addView(TextView(this).apply {
            text = profile.name; textSize = if (isTV) 15f else 12f
            setTextColor(Color.parseColor(if (isActive) "#FFFFFF" else "#AAAAAA"))
            gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        })
        if (isActive) {
            card.addView(TextView(this).apply {
                text = "▶ Aktif"; textSize = if (isTV) 11f else 9f
                setTextColor(Color.parseColor("#4CAF50")); gravity = Gravity.CENTER
            })
        }

        // Focus / click
        card.setOnFocusChangeListener { _, f ->
            avatar.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(Color.parseColor(profile.avatarColor))
                if (isActive || f) setStroke(dp(if (f) 4 else 3), if (f) Color.WHITE else Color.WHITE)
            }
            card.animate().scaleX(if (f && isTV) 1.08f else 1f).scaleY(if (f && isTV) 1.08f else 1f).setDuration(120).start()
        }
        card.setOnClickListener { onProfileSelected(profile) }
        card.setOnKeyListener { _, k, e ->
            if (e.action == KeyEvent.ACTION_DOWN && (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER)) {
                onProfileSelected(profile); true
            } else false
        }
        return card
    }

    private fun buildAddCard(avatarSz: Int): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            isFocusable = true; isFocusableInTouchMode = false; isClickable = true
            val lp = LinearLayout.LayoutParams(0, -2, 1f); lp.setMargins(dp(8), 0, dp(8), 0); layoutParams = lp
        }
        val circle = TextView(this).apply {
            text = "+"; textSize = (avatarSz / resources.displayMetrics.density * 0.4f)
            setTextColor(Color.parseColor("#666666")); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(avatarSz, avatarSz).also { it.bottomMargin = dp(10) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT)
                setStroke(dp(2), Color.parseColor("#444444"))
            }
        }
        card.addView(circle)
        card.addView(TextView(this).apply {
            text = "Tambah Profil"; textSize = if (isTV) 14f else 11f
            setTextColor(Color.parseColor("#666666")); gravity = Gravity.CENTER
        })
        card.setOnFocusChangeListener { _, f ->
            circle.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT)
                setStroke(dp(if (f) 3 else 2), if (f) Color.WHITE else Color.parseColor("#444444"))
            }
            circle.setTextColor(if (f) Color.WHITE else Color.parseColor("#666666"))
            card.animate().scaleX(if (f && isTV) 1.08f else 1f).scaleY(if (f && isTV) 1.08f else 1f).setDuration(120).start()
        }
        card.setOnClickListener { launchManage(addNew = true) }
        return card
    }

    // ── Profile Selected ──────────────────────────────────────────────────────

    private fun onProfileSelected(profile: MtsProfile) {
        if (profile.pinHash != null) {
            // Has PIN — go to PIN screen
            val intent = Intent(this, ProfilePinActivity::class.java)
            intent.putExtra("profile_id", profile.id)
            startActivityForResult(intent, 7001)
        } else {
            switchToProfile(profile)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 7001 && resultCode == RESULT_OK) {
            val pid = data?.getStringExtra("profile_id") ?: return
            val profile = ProfileManager.loadProfiles(this).find { it.id == pid } ?: return
            switchToProfile(profile)
        }
        if (requestCode == 7002) loadProfiles() // returned from manage
    }

    private fun switchToProfile(profile: MtsProfile) {
        progressBar.visibility = View.VISIBLE
        tvTitle.text = "Memuatkan profil..."
        val email = PreferenceManager.getDefaultSharedPreferences(this)
            .getString(ProfileSwitchActivity.KEY_ACTIVE_EMAIL, "") ?: ""
        lifecycleScope.launch(Dispatchers.IO) {
            ProfileManager.setActiveProfile(this@ProfilePickerActivity, profile)
            try {
                val syncKey = ProfileManager.cloudSyncKey(email, profile.id)
                com.mts.mtsflix.cloud.MTSFlixCloudSync.restoreWatchHistoryByKey(
                    this@ProfilePickerActivity, syncKey)
            } catch (e: Exception) {}
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                val intent = Intent(this@ProfilePickerActivity,
                    Class.forName("com.lagradost.cloudstream3.MainActivity"))
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent); finish()
            }
        }
    }

    private fun launchManage(addNew: Boolean = false) {
        val intent = Intent(this, ProfileManageActivity::class.java)
        intent.putExtra("add_new", addNew)
        startActivityForResult(intent, 7002)
    }

    override fun onBackPressed() { /* force profile selection */ }
}
