package com.mts.mtsflix.license

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * MTSFlix Profile Manager Screen
 * Tambah / Edit / Padam profil — set nama, warna, kanak-kanak, PIN
 */
class ProfileManageActivity : AppCompatActivity() {

    private var isTV = false
    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isTV = (getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager)
            .currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        window.statusBarColor     = Color.parseColor("#141414")
        window.navigationBarColor = Color.parseColor("#141414")
        buildUI()
        loadList()

        // Auto-open add if launched with add_new=true
        if (intent.getBooleanExtra("add_new", false)) {
            openAddDialog()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUI() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#141414")); isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(if (isTV) 80 else 20), dp(if (isTV) 48 else 56), dp(if (isTV) 80 else 20), dp(32))
        }

        // Header
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(28))
            addView(TextView(this@ProfileManageActivity).apply {
                text = "←"; textSize = if (isTV) 20f else 18f; setTextColor(Color.WHITE)
                setPadding(0, 0, dp(16), 0); isFocusable = true; isClickable = true
                setOnClickListener { finish() }
            })
            addView(TextView(this@ProfileManageActivity).apply {
                text = "Urus Profil"; textSize = if (isTV) 24f else 20f
                typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            })
        })

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listContainer)

        // Add new button
        root.addView(buildAddBtn())

        scroll.addView(root); setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom); insets
        }
    }

    private fun loadList() {
        listContainer.removeAllViews()
        val profiles = ProfileManager.loadProfiles(this)
        val active   = ProfileManager.getActiveProfile(this)
        profiles.forEach { listContainer.addView(buildProfileRow(it, it.id == active?.id)) }
    }

    private fun buildProfileRow(p: MtsProfile, isActive: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(12); layoutParams = lp
            setPadding(dp(20), dp(18), dp(16), dp(18))
            isFocusable = true; isClickable = true
            background = rowBg(false)
            setOnFocusChangeListener { v, f -> v.background = rowBg(f) }
        }

        // Avatar
        val av = TextView(this).apply {
            text = p.avatarLetter; textSize = if (isTV) 18f else 15f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            val sz = dp(if (isTV) 60 else 48)
            val lp = LinearLayout.LayoutParams(sz, sz); lp.marginEnd = dp(16); layoutParams = lp
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor(p.avatarColor)) }
        }
        row.addView(av)

        // Info column
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        col.addView(TextView(this).apply {
            val badges = buildString {
                if (p.pinHash != null) append(" 🔒")
                if (p.isKids) append(" 👦Kanak-kanak")
                if (isActive) append(" ✓Aktif")
            }
            text = p.name + badges; textSize = if (isTV) 17f else 14f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
        })
        col.addView(TextView(this).apply {
            text = if (p.isKids) "Profil Kanak-kanak" else "Profil Dewasa"
            textSize = if (isTV) 13f else 11f; setTextColor(Color.parseColor("#888888"))
        })
        row.addView(col)

        // Edit button
        row.addView(buildIconBtn("✏️") { openEditDialog(p) })
        // Delete button (disable if only 1 profile left)
        val profiles = ProfileManager.loadProfiles(this)
        if (profiles.size > 1) row.addView(buildIconBtn("🗑️") { confirmDelete(p) })

        row.setOnClickListener { openEditDialog(p) }
        return row
    }

    private fun buildIconBtn(icon: String, onClick: () -> Unit) = TextView(this).apply {
        text = icon; textSize = if (isTV) 18f else 16f; gravity = Gravity.CENTER
        val lp = LinearLayout.LayoutParams(dp(if (isTV) 48 else 40), dp(if (isTV) 48 else 40))
        lp.marginStart = dp(8); layoutParams = lp
        isFocusable = true; isClickable = true; setOnClickListener { onClick() }
        setOnFocusChangeListener { v, f -> v.animate().scaleX(if (f) 1.2f else 1f).scaleY(if (f) 1.2f else 1f).setDuration(100).start() }
    }

    private fun rowBg(focused: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(12).toFloat()
        setColor(if (focused) Color.parseColor("#2A2A2A") else Color.parseColor("#1E1E1E"))
        if (focused) setStroke(dp(2), Color.WHITE)
    }

    private fun buildAddBtn() = TextView(this).apply {
        text = "+  Tambah Profil Baru"; textSize = if (isTV) 17f else 14f
        typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        val lp = LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(16); layoutParams = lp
        setPadding(dp(24), dp(18), dp(24), dp(18))
        isFocusable = true; isClickable = true
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = dp(12).toFloat()
            setColor(Color.TRANSPARENT); setStroke(dp(2), Color.parseColor("#E50914"))
        }
        setOnFocusChangeListener { v, f ->
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(12).toFloat()
                setColor(if (f) Color.parseColor("#E50914") else Color.TRANSPARENT)
                setStroke(dp(2), Color.parseColor("#E50914"))
            }
            v.animate().scaleX(if (f && isTV) 1.03f else 1f).scaleY(if (f && isTV) 1.03f else 1f).setDuration(120).start()
        }
        setOnClickListener { openAddDialog() }
        setOnKeyListener { _, k, e ->
            if (e.action == KeyEvent.ACTION_DOWN && (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER)) {
                openAddDialog(); true
            } else false
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────

    private fun openAddDialog() {
        if (ProfileManager.loadProfiles(this).size >= ProfileManager.MAX_PROFILES) {
            Toast.makeText(this, "Maksimum ${ProfileManager.MAX_PROFILES} profil sahaja", Toast.LENGTH_SHORT).show()
            return
        }
        showProfileDialog(null)
    }

    private fun openEditDialog(p: MtsProfile) = showProfileDialog(p)

    private fun showProfileDialog(existing: MtsProfile?) {
        val d  = dp(1)
        val isEdit = existing != null

        val dialogRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(16), dp(24), dp(8))
            setBackgroundColor(Color.parseColor("#1E1E1E"))
        }

        // Name input
        dialogRoot.addView(label("Nama Profil"))
        val etName = EditText(this).apply {
            setText(existing?.name ?: ""); hint = "cth: Akmal, Mama, Budak 1"
            setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); textSize = 15f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#2A2A2A")); setStroke(d, Color.parseColor("#444444"))
            }
        }
        dialogRoot.addView(etName)
        dialogRoot.addView(spacer(12))

        // Color picker
        dialogRoot.addView(label("Warna Avatar"))
        val colorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(4), 0, 0) }
        var selectedColor = existing?.avatarColor ?: MtsProfile.AVATAR_COLORS[0]
        val colorBtns = MtsProfile.AVATAR_COLORS.map { hex ->
            TextView(this).apply {
                val sz = dp(if (isTV) 44 else 36); val lp = LinearLayout.LayoutParams(sz, sz); lp.marginEnd = dp(8); layoutParams = lp
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(Color.parseColor(hex))
                    if (hex == selectedColor) setStroke(dp(3), Color.WHITE)
                }
                isFocusable = true; isClickable = true
                setOnClickListener { v ->
                    selectedColor = hex
                    colorRow.children().filterIsInstance<TextView>().forEach { btn ->
                        (btn.background as? GradientDrawable)?.setStroke(0, Color.TRANSPARENT)
                    }
                    (v.background as? GradientDrawable)?.setStroke(dp(3), Color.WHITE)
                }
            }.also { colorRow.addView(it) }
        }
        dialogRoot.addView(colorRow)
        dialogRoot.addView(spacer(12))

        // Kids toggle
        dialogRoot.addView(label("Jenis Profil"))
        var isKids = existing?.isKids ?: false
        val kidsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val btnDewasa = profileTypeBtn("Dewasa", !isKids)
        val btnKids   = profileTypeBtn("Kanak-kanak", isKids)
        btnDewasa.setOnClickListener { isKids = false; styleTypeBtn(btnDewasa, true); styleTypeBtn(btnKids, false) }
        btnKids.setOnClickListener   { isKids = true;  styleTypeBtn(btnDewasa, false); styleTypeBtn(btnKids, true) }
        kidsRow.addView(btnDewasa); kidsRow.addView(spacer(8)); kidsRow.addView(btnKids)
        dialogRoot.addView(kidsRow)
        dialogRoot.addView(spacer(12))

        // PIN section
        dialogRoot.addView(label("PIN (pilihan)"))
        val pinRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val etPin = EditText(this).apply {
            hint = if (existing?.pinHash != null) "Kosong = kekal PIN lama" else "4 digit (kosong = tiada PIN)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); textSize = 15f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            val lp = LinearLayout.LayoutParams(0, -2, 1f); layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#2A2A2A")); setStroke(d, Color.parseColor("#444444"))
            }
        }
        pinRow.addView(etPin)
        if (existing?.pinHash != null) {
            pinRow.addView(spacer(8))
            pinRow.addView(TextView(this).apply {
                text = "Buang PIN"; textSize = 12f; setTextColor(Color.parseColor("#E50914"))
                isFocusable = true; isClickable = true; setPadding(dp(8), 0, 0, 0)
                setOnClickListener {
                    val updated = existing.copy(pinHash = null)
                    ProfileManager.updateProfile(this@ProfileManageActivity, updated)
                    loadList(); Toast.makeText(this@ProfileManageActivity, "PIN dibuang", Toast.LENGTH_SHORT).show()
                }
            })
        }
        dialogRoot.addView(pinRow)

        AlertDialog.Builder(this)
            .setTitle(if (isEdit) "Edit Profil" else "Profil Baru")
            .setView(dialogRoot)
            .setPositiveButton(if (isEdit) "Simpan" else "Tambah") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isBlank()) { Toast.makeText(this, "Nama diperlukan", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val pinInput = etPin.text.toString().trim()
                val pinHash = when {
                    pinInput.length == 4 && pinInput.all { it.isDigit() } -> MtsProfile.sha256(pinInput)
                    pinInput.isEmpty() && existing?.pinHash != null -> existing.pinHash // keep existing
                    else -> null
                }
                if (isEdit && existing != null) {
                    ProfileManager.updateProfile(this, existing.copy(name = name, avatarColor = selectedColor, isKids = isKids, pinHash = pinHash))
                    Toast.makeText(this, "Profil dikemas kini", Toast.LENGTH_SHORT).show()
                } else {
                    ProfileManager.addProfile(this, name, selectedColor, isKids, if (pinInput.length == 4) pinInput else null)
                    Toast.makeText(this, "Profil ditambah", Toast.LENGTH_SHORT).show()
                }
                loadList()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun confirmDelete(p: MtsProfile) {
        AlertDialog.Builder(this)
            .setTitle("Padam Profil")
            .setMessage("Padam profil \"${p.name}\"?\nData tontonan tidak akan dipadam dari cloud.")
            .setPositiveButton("Padam") { _, _ ->
                ProfileManager.deleteProfile(this, p.id)
                loadList(); Toast.makeText(this, "Profil dipadam", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null).show()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun label(text: String) = TextView(this).apply {
        this.text = text; textSize = 12f; setTextColor(Color.parseColor("#888888"))
        setPadding(0, 0, 0, dp(6)); typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.1f
    }
    private fun spacer(dp: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(-1, this@ProfileManageActivity.dp(dp))
    }
    private fun profileTypeBtn(text: String, active: Boolean) = TextView(this).apply {
        this.text = text; textSize = 13f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (active) Color.WHITE else Color.parseColor("#888888"))
        gravity = Gravity.CENTER; setPadding(dp(20), dp(10), dp(20), dp(10))
        isFocusable = true; isClickable = true
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = dp(8).toFloat()
            setColor(if (active) Color.parseColor("#E50914") else Color.parseColor("#2A2A2A"))
        }
    }
    private fun styleTypeBtn(btn: TextView, active: Boolean) {
        btn.setTextColor(if (active) Color.WHITE else Color.parseColor("#888888"))
        btn.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = dp(8).toFloat()
            setColor(if (active) Color.parseColor("#E50914") else Color.parseColor("#2A2A2A"))
        }
    }
    private fun android.widget.LinearLayout.children() = (0 until childCount).map { getChildAt(it) }
}
