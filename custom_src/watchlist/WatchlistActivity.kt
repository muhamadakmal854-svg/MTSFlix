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
import com.lagradost.cloudstream3.APIHolder
import com.mts.mtsflix.theme.MTSFlixThemeManager

/**
 * MTSFlix Watchlist Activity v1.1.5
 * Paparan senarai tontonan dari sejarah tontonan pengguna.
 * - Bahagian atas: Toggle provider mana yang diaktifkan notifikasi
 * - Bahagian bawah: Senarai show dari sejarah tontonan, setiap satu ada butang 🔔/🔕
 */
class WatchlistActivity : AppCompatActivity() {

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

    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A0F"))
            layoutParams = LinearLayout.LayoutParams(-1, -1)
        }

        // ── Header ──────────────────────────────────────────────────
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(12))
            setBackgroundColor(Color.parseColor("#111118"))
        }

        val tvIcon = TextView(this).apply { text = "🔔"; textSize = 30f; gravity = Gravity.CENTER }
        val tvTitle = TextView(this).apply {
            text = "Senarai Tonton Saya"; textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(4))
        }
        val tvSub = TextView(this).apply {
            text = "Diambil dari sejarah tontonan anda. Hidupkan 🔔 untuk terima notifikasi episod baru."
            textSize = 12f; setTextColor(Color.parseColor("#AAAAAA"))
            gravity = Gravity.CENTER; setPadding(dp(8), 0, dp(8), 0)
        }
        tvCount = TextView(this).apply {
            textSize = 12f; setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0)
        }
        header.addView(tvIcon); header.addView(tvTitle); header.addView(tvSub); header.addView(tvCount)
        root.addView(header)

        // ── Provider Chips Section ────────────────────────────────────
        val providerSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
            setBackgroundColor(Color.parseColor("#0D0D14"))
        }

        val tvProviderLabel = TextView(this).apply {
            text = "PENAPIS PROVIDER — Pilih yang hendak diaktifkan notifikasi:"
            textSize = 11f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E50914")); setPadding(0, 0, 0, dp(8))
        }
        providerSection.addView(tvProviderLabel)

        providerChipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val providerScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(providerChipsRow)
        }
        providerSection.addView(providerScroll)
        root.addView(providerSection)

        // ── Divider ─────────────────────────────────────────────────
        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(1))
            setBackgroundColor(Color.parseColor("#222222"))
        })

        // ── Loading bar ─────────────────────────────────────────────
        progressBar = ProgressBar(this).apply {
            visibility = View.VISIBLE
            layoutParams = LinearLayout.LayoutParams(-2, -2).also {
                it.gravity = Gravity.CENTER; it.topMargin = dp(20)
            }
        }
        root.addView(progressBar)

        // ── Empty state ──────────────────────────────────────────────
        tvEmpty = TextView(this).apply {
            text = "Tiada sejarah tontonan ditemui.\nTonton sesuatu dahulu untuk ia muncul di sini."
            textSize = 14f; setTextColor(Color.parseColor("#555555"))
            gravity = Gravity.CENTER; setPadding(dp(32), dp(40), dp(32), 0)
            visibility = View.GONE
        }
        root.addView(tvEmpty)

        // ── Show list ────────────────────────────────────────────────
        listView = ListView(this).apply {
            divider = null; dividerHeight = dp(8)
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        root.addView(listView)

        setContentView(root)
    }

    // ── Load watch history from DataStoreHelper ──────────────────────
    private fun loadData() {
        progressBar.visibility = View.VISIBLE
        Thread {
            val items = WatchlistManager.getHistoryFromDataStore()
            Handler(Looper.getMainLooper()).post {
                progressBar.visibility = View.GONE
                historyItems = items
                tvCount.text = "${items.size} siri/filem dalam sejarah tontonan"

                if (items.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                    listView.visibility = View.GONE
                } else {
                    tvEmpty.visibility = View.GONE
                    listView.visibility = View.VISIBLE
                    buildProviderChips(items)
                    setupListAdapter(items)
                }
            }
        }.start()
    }

    // ── Provider chips ───────────────────────────────────────────────
    private fun buildProviderChips(items: List<WatchlistManager.WatchHistoryItem>) {
        providerChipsRow.removeAllViews()

        // Get unique API names from history items (intersect with installed APIs)
        val apiNames = items.map { it.apiName }.distinct().sorted()

        if (apiNames.isEmpty()) return

        // "Semua" toggle chip
        val btnAll = buildChip("⚡ Semua", true) {
            apiNames.forEach { WatchlistManager.setProviderNotificationEnabled(this, it, true) }
            buildProviderChips(items)
        }
        providerChipsRow.addView(btnAll)

        apiNames.forEach { apiName ->
            val enabled = WatchlistManager.isProviderNotificationEnabled(this, apiName)
            val chip = buildChip(if (enabled) "✅ $apiName" else "❌ $apiName", enabled) {
                val newState = !WatchlistManager.isProviderNotificationEnabled(this, apiName)
                WatchlistManager.setProviderNotificationEnabled(this, apiName, newState)
                buildProviderChips(items) // rebuild chips
            }
            providerChipsRow.addView(chip)
        }
    }

    private fun buildChip(text: String, active: Boolean, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text; textSize = 12f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (active) Color.WHITE else Color.parseColor("#888888"))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(16).toFloat()
                setColor(if (active) accent else Color.parseColor("#222222"))
                if (!active) setStroke(dp(1), Color.parseColor("#333333"))
            }
            val lp = LinearLayout.LayoutParams(-2, -2); lp.setMargins(0, 0, dp(8), 0); layoutParams = lp
            isFocusable = true; isFocusableInTouchMode = false
            setOnFocusChangeListener { v, f ->
                v.animate().scaleX(if (f) 1.1f else 1f).scaleY(if (f) 1.1f else 1f).setDuration(100).start()
            }
            setOnClickListener { onClick() }
            setOnKeyListener { _, k, e ->
                if (e.action == KeyEvent.ACTION_DOWN && (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER)) {
                    onClick(); true
                } else false
            }
        }
    }

    // ── Show list adapter ────────────────────────────────────────────
    private fun setupListAdapter(items: List<WatchlistManager.WatchHistoryItem>) {
        val adapter = object : ArrayAdapter<WatchlistManager.WatchHistoryItem>(this, 0, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val item = getItem(position) ?: return View(context)
                val notifEnabled = WatchlistManager.isShowNotificationEnabled(context, item.url)
                val providerEnabled = WatchlistManager.isProviderNotificationEnabled(context, item.apiName)

                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(10).toFloat()
                        setColor(Color.parseColor("#141418"))
                    }
                    isFocusable = true; isFocusableInTouchMode = false
                    setOnFocusChangeListener { v, f ->
                        v.background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE; cornerRadius = dp(10).toFloat()
                            setColor(if (f) Color.parseColor("#202028") else Color.parseColor("#141418"))
                        }
                    }
                }

                // Icon / type indicator
                val tvType = TextView(context).apply {
                    text = if (item.url.contains("movie", true)) "🎬" else "📺"
                    textSize = 20f; gravity = Gravity.CENTER
                    val lp = LinearLayout.LayoutParams(dp(38), dp(38)); lp.marginEnd = dp(12); layoutParams = lp
                }
                row.addView(tvType)

                // Name + provider info
                val infoCol = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                }
                val tvName = TextView(context).apply {
                    text = item.name; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE); maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                val tvProvider = TextView(context).apply {
                    val providerStatus = if (providerEnabled) "" else " (Notifikasi Provider OFF)"
                    text = "📡 ${item.apiName}$providerStatus"
                    textSize = 11f
                    setTextColor(if (providerEnabled) Color.parseColor("#888888") else Color.parseColor("#E55050"))
                }
                infoCol.addView(tvName); infoCol.addView(tvProvider)
                row.addView(infoCol)

                // Notification bell toggle
                val tvBell = TextView(context).apply {
                    text = if (notifEnabled) "🔔" else "🔕"
                    textSize = 22f; gravity = Gravity.CENTER
                    val lp = LinearLayout.LayoutParams(dp(44), dp(44)); layoutParams = lp
                    isFocusable = true; isFocusableInTouchMode = false
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (notifEnabled) Color.parseColor("#1A2A1A") else Color.parseColor("#2A1A1A"))
                    }
                    setOnFocusChangeListener { v, f ->
                        v.animate().scaleX(if (f) 1.2f else 1f).scaleY(if (f) 1.2f else 1f).setDuration(100).start()
                    }
                    setOnClickListener {
                        val newState = !WatchlistManager.isShowNotificationEnabled(context, item.url)
                        WatchlistManager.setShowNotificationEnabled(context, item.url, newState)
                        notifyDataSetChanged()
                        val msg = if (newState) "🔔 Notifikasi diaktifkan untuk ${item.name}" else "🔕 Notifikasi dimatikan untuk ${item.name}"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                    setOnKeyListener { _, k, e ->
                        if (e.action == KeyEvent.ACTION_DOWN && (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER)) {
                            performClick(); true
                        } else false
                    }
                }
                row.addView(tvBell)

                // Row click = toggle notification too
                row.setOnClickListener {
                    val newState = !WatchlistManager.isShowNotificationEnabled(context, item.url)
                    WatchlistManager.setShowNotificationEnabled(context, item.url, newState)
                    notifyDataSetChanged()
                    val msg = if (newState) "🔔 Notifikasi diaktifkan untuk ${item.name}" else "🔕 Notifikasi dimatikan untuk ${item.name}"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
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
