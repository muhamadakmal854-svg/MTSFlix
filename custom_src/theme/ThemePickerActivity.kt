package com.mts.mtsflix.theme

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
 * MTSFlix Theme Picker Activity v1.1.5
 * Paparan pilihan 4 tema warna (D-pad / remote TV friendly).
 * Selepas pilih tema, app restart untuk apply perubahan warna.
 */
class ThemePickerActivity : AppCompatActivity() {

    private var selectedKey = MTSFlixThemeManager.THEME_RED
    private val themeCards = mutableListOf<Pair<View, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MTSFlixThemeManager.applyToWindow(this)
        selectedKey = MTSFlixThemeManager.getCurrentThemeKey(this)
        buildUI()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0A0A0F"))
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }

        val tvIcon = TextView(this).apply { text = "🎨"; textSize = 38f; gravity = Gravity.CENTER }
        root.addView(tvIcon)

        val tvTitle = TextView(this).apply {
            text = "Pilih Tema Warna Aplikasi"
            textSize = 22f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(4))
        }
        root.addView(tvTitle)

        val tvSub = TextView(this).apply {
            text = "Pilih satu tema yang sesuai dengan citarasa anda"
            textSize = 13f; setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(28))
        }
        root.addView(tvSub)

        // Theme cards grid (2 x 2)
        val grid = GridLayout(this).apply {
            columnCount = 2
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER }
        }

        MTSFlixThemeManager.themes.entries.forEachIndexed { _, (key, theme) ->
            val card = buildThemeCard(key, theme)
            grid.addView(card)
            themeCards.add(Pair(card, key))
        }
        root.addView(grid)

        // Apply button
        val btnApply = TextView(this).apply {
            text = "✅ Guna Tema"
            textSize = 16f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(dp(220), dp(50))
            lp.topMargin = dp(28); lp.gravity = Gravity.CENTER; layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(MTSFlixThemeManager.getAccentColor(this@ThemePickerActivity))
            }
            isFocusable = true; isFocusableInTouchMode = false
            setOnFocusChangeListener { v, f ->
                v.animate().scaleX(if (f) 1.08f else 1f).scaleY(if (f) 1.08f else 1f).setDuration(120).start()
            }
            setOnClickListener { applyAndRestart() }
            setOnKeyListener { _, k, e ->
                if (e.action == KeyEvent.ACTION_DOWN && (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER)) {
                    applyAndRestart(); true
                } else false
            }
        }
        root.addView(btnApply)

        setContentView(root)
        updateCardSelections()
    }

    private fun buildThemeCard(key: String, theme: MTSFlixThemeManager.ThemeColors): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            val sz = dp(130)
            val lp = GridLayout.LayoutParams(); lp.width = sz; lp.height = sz
            lp.setMargins(dp(12), dp(12), dp(12), dp(12)); layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#1A1A22"))
                setStroke(dp(3), theme.accent)
            }
            isFocusable = true; isFocusableInTouchMode = false
        }

        val tvEmoji = TextView(this).apply { text = theme.emoji; textSize = 30f; gravity = Gravity.CENTER }
        val tvName = TextView(this).apply {
            text = theme.displayName; textSize = 12f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), 0)
        }

        val tvColorStrip = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(6)).also { it.topMargin = dp(6) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(3).toFloat()
                setColor(theme.accent)
            }
        }

        card.addView(tvEmoji); card.addView(tvColorStrip); card.addView(tvName)

        card.setOnFocusChangeListener { v, f ->
            v.animate().scaleX(if (f) 1.12f else 1f).scaleY(if (f) 1.12f else 1f).setDuration(120).start()
        }
        card.setOnClickListener { selectedKey = key; updateCardSelections() }
        card.setOnKeyListener { _, k, e ->
            if (e.action == KeyEvent.ACTION_DOWN && (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER)) {
                selectedKey = key; updateCardSelections(); true
            } else false
        }

        return card
    }

    private fun updateCardSelections() {
        themeCards.forEach { (card, key) ->
            val theme = MTSFlixThemeManager.themes[key]!!
            val isSelected = key == selectedKey
            card.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(if (isSelected) Color.parseColor("#252530") else Color.parseColor("#1A1A22"))
                setStroke(dp(if (isSelected) 4 else 2), if (isSelected) theme.accent else Color.parseColor("#333333"))
            }
        }
    }

    private fun applyAndRestart() {
        MTSFlixThemeManager.setTheme(this, selectedKey)
        Toast.makeText(this, "✅ Tema ${MTSFlixThemeManager.themes[selectedKey]?.displayName} digunakan!", Toast.LENGTH_SHORT).show()
        // Restart app for theme to take full effect
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finishAffinity()
    }
}
