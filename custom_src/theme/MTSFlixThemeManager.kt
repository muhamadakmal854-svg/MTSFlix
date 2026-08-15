package com.mts.mtsflix.theme

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.preference.PreferenceManager

/**
 * MTSFlix Theme Manager v1.1.5
 * Menguruskan pemilihan tema warna UI untuk seluruh aplikasi.
 */
object MTSFlixThemeManager {

    const val PREF_KEY = "mtsflix_ui_theme"

    const val THEME_RED    = "red"
    const val THEME_BLUE   = "blue"
    const val THEME_PURPLE = "purple"
    const val THEME_GOLD   = "gold"

    data class ThemeColors(
        val accent: Int,
        val accentDark: Int,
        val accentLight: Int,
        val statusBar: Int,
        val navBar: Int,
        val focusHighlight: Int,
        val displayName: String,
        val emoji: String
    )

    val themes: Map<String, ThemeColors> = mapOf(
        THEME_RED to ThemeColors(
            accent = Color.parseColor("#E50914"),
            accentDark = Color.parseColor("#A80000"),
            accentLight = Color.parseColor("#FF4444"),
            statusBar = Color.parseColor("#0A0A0F"),
            navBar = Color.parseColor("#0A0A0F"),
            focusHighlight = Color.parseColor("#E50914"),
            displayName = "Netflix Red",
            emoji = "🔴"
        ),
        THEME_BLUE to ThemeColors(
            accent = Color.parseColor("#0063E5"),
            accentDark = Color.parseColor("#0041A8"),
            accentLight = Color.parseColor("#4496FF"),
            statusBar = Color.parseColor("#040720"),
            navBar = Color.parseColor("#040720"),
            focusHighlight = Color.parseColor("#0063E5"),
            displayName = "Disney+ Blue",
            emoji = "🔵"
        ),
        THEME_PURPLE to ThemeColors(
            accent = Color.parseColor("#8A2BE2"),
            accentDark = Color.parseColor("#5A0FA8"),
            accentLight = Color.parseColor("#B05FFF"),
            statusBar = Color.parseColor("#0D0012"),
            navBar = Color.parseColor("#0D0012"),
            focusHighlight = Color.parseColor("#8A2BE2"),
            displayName = "HBO Purple",
            emoji = "🟣"
        ),
        THEME_GOLD to ThemeColors(
            accent = Color.parseColor("#D4AF37"),
            accentDark = Color.parseColor("#9A7D1A"),
            accentLight = Color.parseColor("#F0D060"),
            statusBar = Color.parseColor("#0A0800"),
            navBar = Color.parseColor("#0A0800"),
            focusHighlight = Color.parseColor("#D4AF37"),
            displayName = "VIP Gold",
            emoji = "🟡"
        )
    )

    fun getCurrentThemeKey(context: Context): String {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREF_KEY, THEME_RED) ?: THEME_RED
    }

    fun getCurrentTheme(context: Context): ThemeColors {
        return themes[getCurrentThemeKey(context)] ?: themes[THEME_RED]!!
    }

    fun setTheme(context: Context, themeKey: String) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putString(PREF_KEY, themeKey).apply()
    }

    fun getAccentColor(context: Context): Int {
        return getCurrentTheme(context).accent
    }

    fun applyToWindow(activity: Activity) {
        val theme = getCurrentTheme(activity)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.window?.statusBarColor = theme.statusBar
            activity.window?.navigationBarColor = theme.navBar
        }
    }
}
