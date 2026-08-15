package com.mts.mtsflix.watchlist

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.mts.mtsflix.theme.MTSFlixThemeManager

/**
 * MTSFlix Watchlist Activity v1.1.5 (Updated)
 * 3 bahagian:
 *   1. Toggle jenis notifikasi (Episod, Filem, Siri, Anime, Drama Asia)
 *   2. Toggle penapis provider (hidup/mati per provider)
 *   3. Senarai show dari sejarah tontonan dengan 🔔/🔕 toggle per-show
 */
class WatchlistActivity : AppCompatActivity() {

    private lateinit var notifTypeSection: LinearLayout
    private lateinit var providerChipsRow: LinearLayout
    private lateinit var listView: ListView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvCount: TextView
    private lateinit var tvEmpty: TextView

    private var historyItems = listOf<WatchlistManager.WatchHistoryItem>()
    private val accent get() = MTSFlixThemeManager.getAccentColor(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MTSFlixThemeManager.applyToWindow(this)
        buildUI()
        loadData()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun sp(v: Float) = v * resources.displayMetrics.scaledDensity

    // ═══════════════════════════════════════════════════════════════
    //  BUILD UI
    // ═══════════════════════════════════════════════════════════════
    private fun buildUI() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0A0A0F"))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        scroll.addView(root)

        // ── Header ──────────────────────────────────────────────────
        root.addView(buildHeader())

        // ── Section 1: Notification Types ───────────────────────────
        root.addView(buildSectionLabel("JENIS NOTIFIKASI"))
        notifTypeSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(4))
        }
        buildNotifTypeToggles()
        root.addView(notifTypeSection)

        // ── Divider ──────────────────────────────────────────────────
        root.addView(makeDivider())

        // ── Section 2: Provider Filter ───────────────────────────────
        root.addView(buildSectionLabel("PENAPIS PROVIDER — hidup/matikan notifikasi per-provider:"))
        val providerScrollWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(8))
        }
        providerChipsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val hScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(providerChipsRow)
        }
        providerScrollWrapper.addView(hScroll)
        root.addView(providerScrollWrapper)

        // ── Divider ──────────────────────────────────────────────────
        root.addView(makeDivider())

        // ── Section 3: Show List ─────────────────────────────────────
        root.addView(buildSectionLabel("SEJARAH TONTONAN — tekan 🔔 untuk aktifkan notifikasi episod baru:"))

        tvCount = TextView(this).apply {
            textSize = 12f; setTextColor(Color.parseColor("#666666"))
            setPadding(dp(16), dp(4), dp(16), dp(4))
        }
        root.addView(tvCount)

        progressBar = ProgressBar(this).apply {
            visibility = View.VISIBLE
            layoutParams = LinearLayout.LayoutParams(-2, -2).also {
                it.gravity = Gravity.CENTER; it.topMargin = dp(20); it.bottomMargin = dp(20)
            }
        }
        root.addView(progressBar)

        tvEmpty = TextView(this).apply {
            text = "Tiada sejarah tontonan ditemui.\nTonton sesuatu terlebih dahulu untuk ia muncul di sini."
            textSize = 14f; setTextColor(Color.parseColor("#555555"))
            gravity = Gravity.CENTER; setPadding(dp(32), dp(24), dp(32), dp(24))
            visibility = View.GONE
        }
        root.addView(tvEmpty)

        listView = ListView(this).apply {
            divider = null; dividerHeight = dp(6)
            layoutParams = LinearLayout.LayoutParams(-1, -2)
            isNestedScrollingEnabled = true
        }
        root.addView(listView)

        // Bottom padding
        root.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(-1, dp(40)) })

        setContentView(scroll)
    }

    // ── Header ────────────────────────────────────────────────────────
    private fun buildHeader(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(14))
            setBackgroundColor(Color.parseColor("#0D0D18"))

            addView(TextView(this@WatchlistActivity).apply { text = "🔔"; textSize = 32f; gravity = Gravity.CENTER })
            addView(TextView(this@WatchlistActivity).apply {
                text = "Senarai Tonton & Notifikasi"; textSize = 21f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(4))
            })
            addView(TextView(this@WatchlistActivity).apply {
                text = "Sejarah tontonan anda diambil terus dari CloudStream.\nKonfigurasikan jenis kandungan yang ingin anda terima notifikasi."
                textSize = 12f; setTextColor(Color.parseColor("#999999"))
                gravity = Gravity.CENTER; setPadding(dp(8), 0, dp(8), 0)
            })
        }
    }

    // ── Section label ──────────────────────────────────────────────────
    private fun buildSectionLabel(text: String): View {
        return TextView(this).apply {
            this.text = text; textSize = 11f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(accent); setPadding(dp(16), dp(14), dp(16), dp(6))
        }
    }

    // ── Divider ────────────────────────────────────────────────────────
    private fun makeDivider(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(-1, dp(1))
        setBackgroundColor(Color.parseColor("#1E1E28"))
    }

    // ═══════════════════════════════════════════════════════════════
    //  SECTION 1: Notification Type Toggles
    // ═══════════════════════════════════════════════════════════════
    private fun buildNotifTypeToggles() {
        notifTypeSection.removeAllViews()
        val types = listOf(
            Triple(ContentDiscoveryManager.PREF_NOTIF_NEW_EPISODES, "🔔  Episod baru (untuk show yang anda tonton)", Color.parseColor("#2244AA")),
            Triple(ContentDiscoveryManager.PREF_NOTIF_NEW_MOVIES,   "🎬  Filem baru ditambah ke provider",           Color.parseColor("#AA4422")),
            Triple(ContentDiscoveryManager.PREF_NOTIF_NEW_SERIES,   "📺  Siri TV baru ditambah ke provider",         Color.parseColor("#226644")),
            Triple(ContentDiscoveryManager.PREF_NOTIF_NEW_ANIME,    "🇯🇵  Anime baru (termasuk OVA)",                Color.parseColor("#884499")),
            Triple(ContentDiscoveryManager.PREF_NOTIF_NEW_ASIAN,    "🇨🇳  Drama Asia / China baru",                 Color.parseColor("#AA6622")),
        )
        types.forEach { (key, label, indicatorColor) ->
            notifTypeSection.addView(buildToggleRow(key, label, indicatorColor))
        }
    }

    private fun buildToggleRow(prefKey: String, label: String, indicatorColor: Int): View {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val isOn = prefs.getBoolean(prefKey, true)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#12121C"))
            }
            val lp = LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(6); layoutParams = lp
            isFocusable = true; isFocusableInTouchMode = false
        }

        // Indicator strip
        val strip = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(4), dp(32)).also { it.marginEnd = dp(12) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(2).toFloat()
                setColor(if (isOn) indicatorColor else Color.parseColor("#333333"))
            }
        }
        row.addView(strip)

        // Label
        val tvLabel = TextView(this).apply {
            text = label; textSize = 13f
            setTextColor(if (isOn) Color.WHITE else Color.parseColor("#666666"))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        row.addView(tvLabel)

        // Toggle switch (visual)
        val tvToggle = buildToggleSwitch(isOn)
        row.addView(tvToggle)

        val toggle = {
            val newState = !prefs.getBoolean(prefKey, true)
            prefs.edit().putBoolean(prefKey, newState).apply()
            // Update visuals
            strip.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(2).toFloat()
                setColor(if (newState) indicatorColor else Color.parseColor("#333333"))
            }
            tvLabel.setTextColor(if (newState) Color.WHITE else Color.parseColor("#666666"))
            updateToggleSwitch(tvToggle, newState)
        }

        row.setOnClickListener { toggle() }
        row.setOnFocusChangeListener { v, f ->
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(8).toFloat()
                setColor(if (f) Color.parseColor("#1E1E30") else Color.parseColor("#12121C"))
                if (f) setStroke(dp(1), accent)
            }
        }
        row.setOnKeyListener { _, k, e ->
            if (e.action == KeyEvent.ACTION_DOWN && (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER)) {
                toggle(); true
            } else false
        }
        return row
    }

    private fun buildToggleSwitch(isOn: Boolean): TextView {
        return TextView(this).apply {
            text = if (isOn) "ON" else "OFF"
            textSize = 11f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(10).toFloat()
                setColor(if (isOn) Color.parseColor("#22BB44") else Color.parseColor("#AA2222"))
            }
            val lp = LinearLayout.LayoutParams(dp(48), -2); lp.marginStart = dp(8); layoutParams = lp
        }
    }

    private fun updateToggleSwitch(tv: TextView, isOn: Boolean) {
        tv.text = if (isOn) "ON" else "OFF"
        tv.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = dp(10).toFloat()
            setColor(if (isOn) Color.parseColor("#22BB44") else Color.parseColor("#AA2222"))
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  SECTION 2: Provider chips
    // ═══════════════════════════════════════════════════════════════
    private fun buildProviderChips(items: List<WatchlistManager.WatchHistoryItem>) {
        providerChipsRow.removeAllViews()
        val apiNames = items.map { it.apiName }.distinct().sorted()
        if (apiNames.isEmpty()) return

        providerChipsRow.addView(buildChip("⚡ Semua ON", true) {
            apiNames.forEach { WatchlistManager.setProviderNotificationEnabled(this, it, true) }
            buildProviderChips(items)
        })
        apiNames.forEach { name ->
            val enabled = WatchlistManager.isProviderNotificationEnabled(this, name)
            providerChipsRow.addView(buildChip(if (enabled) "✅ $name" else "❌ $name", enabled) {
                val newState = !WatchlistManager.isProviderNotificationEnabled(this, name)
                WatchlistManager.setProviderNotificationEnabled(this, name, newState)
                buildProviderChips(items)
            })
        }
    }

    private fun buildChip(text: String, active: Boolean, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text; textSize = 12f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (active) Color.WHITE else Color.parseColor("#888888"))
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(16).toFloat()
                setColor(if (active) accent else Color.parseColor("#1E1E1E"))
                if (!active) setStroke(dp(1), Color.parseColor("#333333"))
            }
            val lp = LinearLayout.LayoutParams(-2, -2); lp.setMargins(0, 0, dp(8), 0); layoutParams = lp
            isFocusable = true; isFocusableInTouchMode = false
            setOnFocusChangeListener { v, f ->
                v.animate().scaleX(if (f) 1.08f else 1f).scaleY(if (f) 1.08f else 1f).setDuration(100).start()
            }
            setOnClickListener { onClick() }
            setOnKeyListener { _, k, e ->
                if (e.action == KeyEvent.ACTION_DOWN && (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER)) {
                    onClick(); true
                } else false
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  SECTION 3: Show List
    // ═══════════════════════════════════════════════════════════════
    private fun loadData() {
        progressBar.visibility = View.VISIBLE
        Thread {
            val items = WatchlistManager.getHistoryFromDataStore()
            Handler(Looper.getMainLooper()).post {
                progressBar.visibility = View.GONE
                historyItems = items
                tvCount.text = "${items.size} siri / filem dalam sejarah tontonan"
                if (items.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE; listView.visibility = View.GONE
                } else {
                    tvEmpty.visibility = View.GONE; listView.visibility = View.VISIBLE
                    buildProviderChips(items)
                    setupListAdapter(items)
                }
            }
        }.start()
    }

    private fun setupListAdapter(items: List<WatchlistManager.WatchHistoryItem>) {
        val adapter = object : ArrayAdapter<WatchlistManager.WatchHistoryItem>(this, 0, items) {
            override fun getView(pos: Int, cv: View?, parent: ViewGroup): View {
                val item = getItem(pos) ?: return View(context)
                val notifOn = WatchlistManager.isShowNotificationEnabled(context, item.url)
                val providerOn = WatchlistManager.isProviderNotificationEnabled(context, item.apiName)

                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    val lp = LinearLayout.LayoutParams(-1, -2); lp.setMargins(dp(12), 0, dp(12), 0); layoutParams = lp
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(10).toFloat()
                        setColor(Color.parseColor("#141420"))
                        if (notifOn) setStroke(dp(1), Color.parseColor("#224433"))
                    }
                    isFocusable = true; isFocusableInTouchMode = false
                    setOnFocusChangeListener { v, f ->
                        v.background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE; cornerRadius = dp(10).toFloat()
                            setColor(if (f) Color.parseColor("#1E1E30") else Color.parseColor("#141420"))
                            if (notifOn || f) setStroke(dp(1), if (f) accent else Color.parseColor("#224433"))
                        }
                    }
                }

                // Type icon
                val tvType = TextView(context).apply {
                    text = when (item.url) {
                        else -> if (item.name.lowercase().contains("movie") || item.name.lowercase().contains("filem")) "🎬" else "📺"
                    }
                    textSize = 20f; gravity = Gravity.CENTER
                    val lp = LinearLayout.LayoutParams(dp(36), dp(36)); lp.marginEnd = dp(12); layoutParams = lp
                }
                row.addView(tvType)

                // Name + provider + status
                val col = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                }
                col.addView(TextView(context).apply {
                    text = item.name; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE); maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                col.addView(TextView(context).apply {
                    val provStr = if (!providerOn) " ⚠️ Provider dimatikan" else ""
                    text = "📡 ${item.apiName}$provStr"
                    textSize = 11f
                    setTextColor(if (providerOn) Color.parseColor("#888888") else Color.parseColor("#CC4444"))
                })
                if (notifOn) {
                    val lastCount = WatchlistManager.getLastEpisodeCount(context, item.url)
                    if (lastCount > 0) {
                        col.addView(TextView(context).apply {
                            text = "Terakhir dikesan: $lastCount episod"
                            textSize = 10f; setTextColor(Color.parseColor("#556655"))
                        })
                    }
                }
                row.addView(col)

                // Bell toggle button
                val bell = TextView(context).apply {
                    text = if (notifOn) "🔔" else "🔕"
                    textSize = 22f; gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (notifOn) Color.parseColor("#1A3A1A") else Color.parseColor("#2A1A1A"))
                    }
                    val lp = LinearLayout.LayoutParams(dp(46), dp(46)); lp.marginStart = dp(8); layoutParams = lp
                    isFocusable = true; isFocusableInTouchMode = false
                    setOnFocusChangeListener { v, f ->
                        v.animate().scaleX(if (f) 1.2f else 1f).scaleY(if (f) 1.2f else 1f).setDuration(100).start()
                    }
                    val toggleFn = {
                        val newState = !WatchlistManager.isShowNotificationEnabled(context, item.url)
                        WatchlistManager.setShowNotificationEnabled(context, item.url, newState)
                        val msg = if (newState) "🔔 Notifikasi diaktifkan: ${item.name}" else "🔕 Notifikasi dimatikan: ${item.name}"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        notifyDataSetChanged()
                    }
                    setOnClickListener { toggleFn() }
                    setOnKeyListener { _, k, e ->
                        if (e.action == KeyEvent.ACTION_DOWN && (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER)) {
                            toggleFn(); true
                        } else false
                    }
                }
                row.addView(bell)

                row.setOnClickListener {
                    val newState = !WatchlistManager.isShowNotificationEnabled(context, item.url)
                    WatchlistManager.setShowNotificationEnabled(context, item.url, newState)
                    Toast.makeText(context, if (newState) "🔔 ${item.name}" else "🔕 ${item.name}", Toast.LENGTH_SHORT).show()
                    notifyDataSetChanged()
                }
                row.setOnKeyListener { _, k, e ->
                    if (e.action == KeyEvent.ACTION_DOWN && (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER)) {
                        row.callOnClick(); true
                    } else false
                }
                return row
            }
        }
        listView.adapter = adapter
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true }
        return super.onKeyDown(keyCode, event)
    }
}
