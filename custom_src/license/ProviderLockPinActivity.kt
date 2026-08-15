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
 * MTSFlix Provider Lock PIN Activity v1.1.4
 * Paparan PIN 4-digit untuk buka Provider terkunci atau tetapkan PIN baru.
 */
class ProviderLockPinActivity : AppCompatActivity() {

    companion object {
        const val MODE_VERIFY_PROVIDER = "verify_provider"
        const val MODE_SET_PIN = "set_pin"
        const val MODE_SETTINGS_ACCESS = "settings_access"
        const val EXTRA_MODE = "mode"
        const val EXTRA_PROVIDER_NAME = "provider_name"
    }

    private val pin = StringBuilder()
    private val MAX = 4
    private val MAX_ATTEMPTS = 3
    private var attempts = 0

    private lateinit var dots: List<View>
    private lateinit var tvHint: TextView
    private lateinit var tvTitle: TextView
    private var mode = MODE_VERIFY_PROVIDER
    private var providerName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0A0A0F")
        window.navigationBarColor = Color.parseColor("#0A0A0F")

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_VERIFY_PROVIDER
        providerName = intent.getStringExtra(EXTRA_PROVIDER_NAME) ?: ""

        buildUI()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0A0A0F"))
            layoutParams = LinearLayout.LayoutParams(-1, -1)
        }

        // Icon Header
        val iconTv = TextView(this).apply {
            text = "🔒"; textSize = 40f
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(-2, -2); lp.bottomMargin = dp(8); layoutParams = lp
        }
        root.addView(iconTv)

        // Title
        val titleText = when (mode) {
            MODE_SET_PIN -> "Tetapkan PIN 4-Digit Baru"
            MODE_SETTINGS_ACCESS -> "Pengurusan Kunci Provider"
            else -> if (providerName.isNotEmpty()) "Provider Terkunci: $providerName" else "Provider Terkunci"
        }

        tvTitle = TextView(this).apply {
            text = titleText; textSize = 20f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(dp(16), 0, dp(16), dp(6))
        }
        root.addView(tvTitle)

        // Hint Text
        val hintText = when (mode) {
            MODE_SET_PIN -> "Masukkan 4 digit PIN pilihan anda"
            MODE_SETTINGS_ACCESS -> "Masukkan PIN keselamatan untuk mengakses tetapan"
            else -> "Masukkan PIN 4-digit untuk mengakses Provider ini"
        }

        tvHint = TextView(this).apply {
            text = hintText; textSize = 14f; setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER; setPadding(dp(16), 0, dp(16), dp(24))
        }
        root.addView(tvHint)

        // PIN Dots
        val dotsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(32))
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
            val sz = dp(68)
            val lp = GridLayout.LayoutParams(); lp.width = sz; lp.height = sz
            lp.setMargins(dp(10), dp(8), dp(10), dp(8)); layoutParams = lp
            isFocusable = label.isNotEmpty(); isFocusableInTouchMode = false
            isClickable = label.isNotEmpty()
            if (label.isNotEmpty()) {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(Color.parseColor("#222222"))
                }
                setOnFocusChangeListener { v, f ->
                    v.background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (f) Color.parseColor("#E50914") else Color.parseColor("#222222"))
                    }
                    v.animate().scaleX(if (f) 1.15f else 1f).scaleY(if (f) 1.15f else 1f).setDuration(100).start()
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
        if (pin.length == MAX) processInput()
    }

    private fun onDelete() {
        if (pin.isNotEmpty()) { pin.deleteCharAt(pin.length - 1); updateDots() }
    }

    private fun updateDots() {
        dots.forEachIndexed { i, dot ->
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (i < pin.length) Color.parseColor("#E50914") else Color.parseColor("#333333"))
            }
        }
    }

    private fun processInput() {
        val enteredPin = pin.toString()

        if (mode == MODE_SET_PIN) {
            ProviderLockManager.setPin(this, enteredPin)
            Toast.makeText(this, "✅ PIN 4-digit berjaya ditetapkan!", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
            return
        }

        if (ProviderLockManager.verifyPin(this, enteredPin)) {
            if (mode == MODE_VERIFY_PROVIDER) {
                ProviderLockManager.unlockSession(providerName)
                Toast.makeText(this, "✅ PIN Sah — Akses Dibenarkan", Toast.LENGTH_SHORT).show()
            }
            val resIntent = Intent()
            resIntent.putExtra(EXTRA_PROVIDER_NAME, providerName)
            setResult(RESULT_OK, resIntent)
            finish()
        } else {
            attempts++
            pin.clear(); updateDots()
            if (attempts >= MAX_ATTEMPTS) {
                tvHint.text = "Terlalu banyak cubaan. Cuba lagi dalam 30 saat."
                tvHint.setTextColor(Color.parseColor("#E50914"))
                window.decorView.postDelayed({
                    attempts = 0
                    tvHint.text = "Masukkan PIN"
                    tvHint.setTextColor(Color.parseColor("#888888"))
                }, 30000)
            } else {
                val left = MAX_ATTEMPTS - attempts
                tvHint.text = "❌ PIN Salah — $left cubaan lagi"
                tvHint.setTextColor(Color.parseColor("#E50914"))
                window.decorView.postDelayed({
                    if (attempts < MAX_ATTEMPTS) {
                        tvHint.text = "Masukkan PIN 4-digit"
                        tvHint.setTextColor(Color.parseColor("#888888"))
                    }
                }, 1500)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            setResult(RESULT_CANCELED)
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
