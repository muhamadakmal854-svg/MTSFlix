package com.mts.mtsflix.license

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/**
 * MTSFlix PIN Entry Screen
 * Papan kekunci 4 digit — mesra TV dan remote
 */
class ProfilePinActivity : AppCompatActivity() {

    private val pin   = StringBuilder()
    private val MAX   = 4
    private val MAX_ATTEMPTS = 3
    private var attempts = 0
    private lateinit var dots: List<View>
    private lateinit var tvHint: TextView
    private lateinit var profile: MtsProfile

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor     = Color.parseColor("#0A0A0F")
        window.navigationBarColor = Color.parseColor("#0A0A0F")

        val profileId = intent.getStringExtra("profile_id") ?: run { finish(); return }
        profile = ProfileManager.loadProfiles(this).find { it.id == profileId } ?: run { finish(); return }

        buildUI()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0A0A0F"))
            layoutParams = LinearLayout.LayoutParams(-1, -1)
        }

        // Avatar
        val av = TextView(this).apply {
            text = profile.avatarLetter; textSize = 32f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(dp(88), dp(88)); lp.bottomMargin = dp(12); layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(Color.parseColor(profile.avatarColor))
            }
        }
        root.addView(av)

        root.addView(TextView(this).apply {
            text = profile.name; textSize = 20f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(6))
        })

        tvHint = TextView(this).apply {
            text = "Masukkan PIN"; textSize = 14f; setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(28))
        }
        root.addView(tvHint)

        // PIN dots
        val dotsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(40))
        }
        dots = (0 until MAX).map {
            View(this).apply {
                val lp = LinearLayout.LayoutParams(dp(16), dp(16)); lp.setMargins(dp(10), 0, dp(10), 0); layoutParams = lp
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#333333")) }
            }.also { dotsRow.addView(it) }
        }
        root.addView(dotsRow)

        // Numpad
        val numpad = GridLayout(this).apply {
            columnCount = 3; setPadding(dp(24), 0, dp(24), 0)
        }
        val numLayout = listOf(
            listOf("1","2","3"),
            listOf("4","5","6"),
            listOf("7","8","9"),
            listOf("","0","⌫")
        )
        numLayout.forEach { row ->
            row.forEach { label ->
                numpad.addView(buildNumKey(label))
            }
        }
        root.addView(numpad)

        setContentView(root)
    }

    private fun buildNumKey(label: String): View {
        val tv = TextView(this).apply {
            text = label; textSize = 24f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            val sz = dp(72)
            val lp = GridLayout.LayoutParams(); lp.width = sz; lp.height = sz
            lp.setMargins(dp(12), dp(10), dp(12), dp(10)); layoutParams = lp
            isFocusable = label.isNotEmpty(); isFocusableInTouchMode = false
            isClickable = label.isNotEmpty()
            if (label.isNotEmpty()) {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(Color.parseColor("#222222"))
                }
                setOnFocusChangeListener { v, f ->
                    v.background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (f) Color.parseColor("#444444") else Color.parseColor("#222222"))
                    }
                    v.animate().scaleX(if (f) 1.12f else 1f).scaleY(if (f) 1.12f else 1f).setDuration(100).start()
                }
                setOnClickListener {
                    when (label) {
                        "⌫" -> onDelete()
                        else -> onDigit(label)
                    }
                }
                setOnKeyListener { _, k, e ->
                    if (e.action == KeyEvent.ACTION_DOWN && (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER)) {
                        when (label) { "⌫" -> onDelete(); else -> onDigit(label) }; true
                    } else false
                }
            }
        }
        return tv
    }

    private fun onDigit(d: String) {
        if (pin.length >= MAX) return
        pin.append(d)
        updateDots()
        if (pin.length == MAX) verifyPin()
    }

    private fun onDelete() {
        if (pin.isNotEmpty()) { pin.deleteCharAt(pin.length - 1); updateDots() }
    }

    private fun updateDots() {
        dots.forEachIndexed { i, dot ->
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (i < pin.length) Color.WHITE else Color.parseColor("#333333"))
            }
        }
    }

    private fun verifyPin() {
        if (profile.checkPin(pin.toString())) {
            val result = Intent()
            result.putExtra("profile_id", profile.id)
            setResult(RESULT_OK, result)
            finish()
        } else {
            attempts++
            pin.clear(); updateDots()
            if (attempts >= MAX_ATTEMPTS) {
                tvHint.text = "Terlalu banyak cubaan. Cuba lagi sebentar."
                tvHint.setTextColor(Color.parseColor("#E50914"))
                // Lock for 30 seconds
                window.decorView.postDelayed({
                    attempts = 0
                    tvHint.text = "Masukkan PIN"
                    tvHint.setTextColor(Color.parseColor("#888888"))
                }, 30000)
            } else {
                val left = MAX_ATTEMPTS - attempts
                tvHint.text = "PIN salah — $left cubaan lagi"
                tvHint.setTextColor(Color.parseColor("#E50914"))
                window.decorView.postDelayed({
                    if (attempts < MAX_ATTEMPTS) {
                        tvHint.text = "Masukkan PIN"
                        tvHint.setTextColor(Color.parseColor("#888888"))
                    }
                }, 1500)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true }
        return super.onKeyDown(keyCode, event)
    }
}
