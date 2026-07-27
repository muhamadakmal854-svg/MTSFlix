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
 * MTSFlix Profile Manager v2 — Fix:
 * - Color grid (4 per row, no cutoff)
 * - Kanak-kanak button visible
 * - Emoji avatar picker (like Netflix)
 * - Kids content filter (cartoon/anime only)
 */
class ProfileManageActivity : AppCompatActivity() {

    private var isTV = false
    private lateinit var listContainer: LinearLayout

    // Netflix-style emoji avatars
    private val EMOJI_AVATARS = listOf(
        "🦁","🐶","🐱","🦊","🐸","🐧","🦄","🐼",
        "🦖","🚀","⭐","🎭","👾","🌈","🎮","🏆",
        "🎵","🌙","🔥","💎","🎯","🌺","🐉","🦋"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isTV = (getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager)
            .currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        window.statusBarColor     = Color.parseColor("#141414")
        window.navigationBarColor = Color.parseColor("#141414")
        buildUI()
        loadList()
        if (intent.getBooleanExtra("add_new", false)) openAddDialog()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUI() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#141414")); isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(if (isTV) 80 else 20), dp(if (isTV) 48 else 56),
                       dp(if (isTV) 80 else 20), dp(32))
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

        // Avatar (emoji or letter)
        val av = TextView(this).apply {
            text = if (p.avatarEmoji != null) p.avatarEmoji else p.avatarLetter
            textSize = if (isTV) 20f else 16f
            typeface = if (p.avatarEmoji == null) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            val sz = dp(if (isTV) 60 else 48)
            val lp = LinearLayout.LayoutParams(sz, sz); lp.marginEnd = dp(16); layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (p.avatarEmoji == null) Color.parseColor(p.avatarColor) else Color.parseColor("#2A2A2A"))
            }
        }
        row.addView(av)

        // Info
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        col.addView(TextView(this).apply {
            val badges = buildString {
                if (p.pinHash != null) append("  🔒")
                if (p.isKids) append("  👦")
                if (isActive) append("  ✓")
            }
            text = p.name + badges; textSize = if (isTV) 17f else 14f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
        })
        col.addView(TextView(this).apply {
            text = if (p.isKids) "Kanak-kanak • Kartun & Animasi sahaja"
                   else "Dewasa • Semua kandungan"
            textSize = if (isTV) 13f else 11f; setTextColor(Color.parseColor("#888888"))
        })
        row.addView(col)

        row.addView(buildIconBtn("✏️") { openEditDialog(p) })
        if (ProfileManager.loadProfiles(this).size > 1)
            row.addView(buildIconBtn("🗑️") { confirmDelete(p) })

        row.setOnClickListener { openEditDialog(p) }
        return row
    }

    private fun buildIconBtn(icon: String, onClick: () -> Unit) = TextView(this).apply {
        text = icon; textSize = if (isTV) 18f else 16f; gravity = Gravity.CENTER
        val sz = dp(if (isTV) 48 else 40)
        val lp = LinearLayout.LayoutParams(sz, sz); lp.marginStart = dp(8); layoutParams = lp
        isFocusable = true; isClickable = true; setOnClickListener { onClick() }
        setOnFocusChangeListener { v, f ->
            v.animate().scaleX(if (f) 1.2f else 1f).scaleY(if (f) 1.2f else 1f).setDuration(100).start()
        }
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
            v.animate().scaleX(if (f && isTV) 1.03f else 1f)
                       .scaleY(if (f && isTV) 1.03f else 1f).setDuration(120).start()
        }
        setOnClickListener { openAddDialog() }
    }

    // ── Dialog Add/Edit ───────────────────────────────────────────────────────

    private fun openAddDialog() {
        if (ProfileManager.loadProfiles(this).size >= ProfileManager.MAX_PROFILES) {
            Toast.makeText(this, "Maksimum ${ProfileManager.MAX_PROFILES} profil sahaja", Toast.LENGTH_SHORT).show()
            return
        }
        showProfileDialog(null)
    }
    private fun openEditDialog(p: MtsProfile) = showProfileDialog(p)

    private fun showProfileDialog(existing: MtsProfile?) {
        val isEdit = existing != null
        var selectedColor = existing?.avatarColor ?: MtsProfile.AVATAR_COLORS[0]
        var selectedEmoji: String? = existing?.avatarEmoji
        var isKids = existing?.isKids ?: false

        val sv = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#1E1E1E")) }
        val dialogRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(8))
        }
        sv.addView(dialogRoot)

        // ── Name ─────────────────────────────────────────────────────────────
        dialogRoot.addView(label("NAMA PROFIL"))
        val etName = EditText(this).apply {
            setText(existing?.name ?: ""); hint = "cth: Akmal, Mama, Anak 1"
            setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); textSize = 15f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = fieldBg()
            val lp = LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(16); layoutParams = lp
        }
        dialogRoot.addView(etName)

        // ── Emoji Avatar (like Netflix) ───────────────────────────────────────
        dialogRoot.addView(label("LOGO PROFIL"))

        // Preview row
        val previewWrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val previewAv = TextView(this).apply {
            text = selectedEmoji ?: (existing?.name?.firstOrNull()?.uppercaseChar()?.toString() ?: "A")
            textSize = 26f; gravity = Gravity.CENTER
            val sz = dp(56); layoutParams = LinearLayout.LayoutParams(sz, sz).also { it.marginEnd = dp(12) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (selectedEmoji == null) Color.parseColor(selectedColor) else Color.parseColor("#2A2A2A"))
            }
            setTextColor(Color.WHITE)
        }
        previewWrap.addView(previewAv)
        previewWrap.addView(TextView(this).apply {
            text = "Pilih logo atau warna di bawah"; textSize = 12f; setTextColor(Color.parseColor("#888888"))
        })
        dialogRoot.addView(previewWrap)

        // Emoji grid — 6 per row
        val emojiGrid = GridLayout(this).apply { columnCount = 6; setPadding(0, dp(4), 0, dp(8)) }
        val emojiBtns = EMOJI_AVATARS.map { emoji ->
            TextView(this).apply {
                text = emoji; textSize = 22f; gravity = Gravity.CENTER
                val sz = dp(44)
                val lp = GridLayout.LayoutParams(); lp.width = sz; lp.height = sz
                lp.setMargins(dp(4), dp(4), dp(4), dp(4)); layoutParams = lp
                isFocusable = true; isClickable = true
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (selectedEmoji == emoji) Color.parseColor("#333333") else Color.TRANSPARENT)
                    if (selectedEmoji == emoji) setStroke(dp(2), Color.WHITE)
                }
                setOnClickListener {
                    selectedEmoji = emoji
                    previewAv.text = emoji
                    previewAv.background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL; setColor(Color.parseColor("#2A2A2A"))
                    }
                    // Update all emoji button backgrounds
                    emojiGrid.children().filterIsInstance<TextView>().forEach { btn ->
                        btn.background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(if (btn.text == emoji) Color.parseColor("#333333") else Color.TRANSPARENT)
                            if (btn.text == emoji) setStroke(dp(2), Color.WHITE)
                        }
                    }
                }
            }.also { emojiGrid.addView(it) }
        }
        dialogRoot.addView(emojiGrid)

        // ── Color picker — GRID 4 per row ─────────────────────────────────────
        dialogRoot.addView(label("WARNA AVATAR (jika tiada logo)"))
        val colorGrid = GridLayout(this).apply { columnCount = 4; setPadding(0, dp(4), 0, dp(4)) }
        val colorBtns = MtsProfile.AVATAR_COLORS.map { hex ->
            View(this).apply {
                val sz = dp(44)
                val lp = GridLayout.LayoutParams(); lp.width = sz; lp.height = sz
                lp.setMargins(dp(6), dp(6), dp(6), dp(6)); layoutParams = lp
                isFocusable = true; isClickable = true
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(Color.parseColor(hex))
                    if (hex == selectedColor) setStroke(dp(3), Color.WHITE)
                }
                setOnClickListener {
                    selectedColor = hex; selectedEmoji = null
                    previewAv.text = etName.text.toString().firstOrNull()?.uppercaseChar()?.toString() ?: "A"
                    previewAv.background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL; setColor(Color.parseColor(hex))
                    }
                    // Clear emoji selection
                    emojiGrid.children().filterIsInstance<TextView>().forEach { btn ->
                        btn.background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT)
                        }
                    }
                    // Update color buttons
                    colorGrid.children().forEach { btn ->
                        (btn.background as? GradientDrawable)?.setStroke(
                            if ((btn.tag as? String) == hex) dp(3) else 0,
                            Color.WHITE
                        )
                    }
                }
                tag = hex
            }.also { colorGrid.addView(it) }
        }
        val lp2 = LinearLayout.LayoutParams(-1, -2); lp2.bottomMargin = dp(16)
        colorGrid.layoutParams = lp2
        dialogRoot.addView(colorGrid)

        // ── Jenis Profil — BOTH buttons side by side ─────────────────────────
        dialogRoot.addView(label("JENIS PROFIL"))
        val typeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(4); layoutParams = lp
        }

        // Dewasa button
        val btnDewasa = TextView(this).apply {
            text = "👤 Dewasa"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (!isKids) Color.WHITE else Color.parseColor("#888888"))
            gravity = Gravity.CENTER; setPadding(dp(12), dp(12), dp(12), dp(12))
            isFocusable = true; isClickable = true
            val lp = LinearLayout.LayoutParams(0, -2, 1f); lp.marginEnd = dp(8); layoutParams = lp
            background = typeBg(!isKids)
        }
        // Kanak-kanak button
        val btnKids = TextView(this).apply {
            text = "👦 Kanak-kanak"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isKids) Color.WHITE else Color.parseColor("#888888"))
            gravity = Gravity.CENTER; setPadding(dp(12), dp(12), dp(12), dp(12))
            isFocusable = true; isClickable = true
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            background = typeBg(isKids)
        }

        btnDewasa.setOnClickListener {
            isKids = false
            btnDewasa.background = typeBg(true);  btnDewasa.setTextColor(Color.WHITE)
            btnKids.background   = typeBg(false); btnKids.setTextColor(Color.parseColor("#888888"))
        }
        btnKids.setOnClickListener {
            isKids = true
            btnKids.background   = typeBg(true);  btnKids.setTextColor(Color.WHITE)
            btnDewasa.background = typeBg(false); btnDewasa.setTextColor(Color.parseColor("#888888"))
        }

        typeRow.addView(btnDewasa); typeRow.addView(btnKids)
        dialogRoot.addView(typeRow)

        // Kids info note
        val kidsNote = TextView(this).apply {
            text = "ℹ️ Profil kanak-kanak hanya papar kandungan kartun & animasi"
            textSize = 11f; setTextColor(Color.parseColor("#4CAF50"))
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.topMargin = dp(6); lp.bottomMargin = dp(16); layoutParams = lp
            visibility = if (isKids) View.VISIBLE else View.GONE
        }
        btnKids.setOnClickListener {
            isKids = true
            btnKids.background   = typeBg(true);  btnKids.setTextColor(Color.WHITE)
            btnDewasa.background = typeBg(false); btnDewasa.setTextColor(Color.parseColor("#888888"))
            kidsNote.visibility  = View.VISIBLE
        }
        btnDewasa.setOnClickListener {
            isKids = false
            btnDewasa.background = typeBg(true);  btnDewasa.setTextColor(Color.WHITE)
            btnKids.background   = typeBg(false); btnKids.setTextColor(Color.parseColor("#888888"))
            kidsNote.visibility  = View.GONE
        }
        dialogRoot.addView(kidsNote)

        // ── PIN ───────────────────────────────────────────────────────────────
        dialogRoot.addView(label("PIN (PILIHAN — 4 DIGIT)"))
        val pinRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val etPin = EditText(this).apply {
            hint = if (existing?.pinHash != null) "Kosong = kekal PIN lama" else "4 digit (kosong = tiada PIN)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); textSize = 15f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            background = fieldBg()
        }
        pinRow.addView(etPin)
        if (existing?.pinHash != null) {
            val lp = LinearLayout.LayoutParams(-2, -2); lp.marginStart = dp(12)
            pinRow.addView(TextView(this).apply {
                text = "Buang PIN"; textSize = 12f; setTextColor(Color.parseColor("#E50914"))
                layoutParams = lp; isFocusable = true; isClickable = true
                setOnClickListener {
                    ProfileManager.updateProfile(this@ProfileManageActivity, existing.copy(pinHash = null))
                    loadList(); Toast.makeText(this@ProfileManageActivity, "PIN dibuang", Toast.LENGTH_SHORT).show()
                }
            })
        }
        dialogRoot.addView(pinRow)

        // ── Save ──────────────────────────────────────────────────────────────
        AlertDialog.Builder(this)
            .setTitle(if (isEdit) "Edit Profil" else "Profil Baru")
            .setView(sv)
            .setPositiveButton(if (isEdit) "Simpan" else "Tambah") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, "Nama diperlukan", Toast.LENGTH_SHORT).show(); return@setPositiveButton
                }
                val pinInput = etPin.text.toString().trim()
                val pinHash = when {
                    pinInput.length == 4 && pinInput.all { it.isDigit() } -> MtsProfile.sha256(pinInput)
                    pinInput.isEmpty() && existing?.pinHash != null -> existing.pinHash
                    else -> null
                }
                if (isEdit && existing != null) {
                    ProfileManager.updateProfile(this, existing.copy(
                        name = name, avatarColor = selectedColor,
                        avatarEmoji = selectedEmoji, isKids = isKids, pinHash = pinHash
                    ))
                    Toast.makeText(this, "Profil dikemas kini ✓", Toast.LENGTH_SHORT).show()
                } else {
                    ProfileManager.addProfile(this, name, selectedColor, isKids,
                        if (pinInput.length == 4) pinInput else null, selectedEmoji)
                    Toast.makeText(this, "Profil ditambah ✓", Toast.LENGTH_SHORT).show()
                }
                loadList()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun confirmDelete(p: MtsProfile) {
        AlertDialog.Builder(this)
            .setTitle("Padam Profil")
            .setMessage("Padam profil \"${p.name}\"?")
            .setPositiveButton("Padam") { _, _ ->
                ProfileManager.deleteProfile(this, p.id); loadList()
                Toast.makeText(this, "Profil dipadam", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null).show()
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text; textSize = 11f; setTextColor(Color.parseColor("#888888"))
        typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.12f
        val lp = LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(4); lp.bottomMargin = dp(6); layoutParams = lp
    }
    private fun fieldBg() = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(8).toFloat()
        setColor(Color.parseColor("#2A2A2A")); setStroke(dp(1), Color.parseColor("#444444"))
    }
    private fun typeBg(active: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(10).toFloat()
        setColor(if (active) Color.parseColor("#E50914") else Color.parseColor("#2A2A2A"))
        if (!active) setStroke(dp(1), Color.parseColor("#444444"))
    }
    private fun GridLayout.children() = (0 until childCount).map { getChildAt(it) }
    private fun android.widget.LinearLayout.children() = (0 until childCount).map { getChildAt(it) }
}
