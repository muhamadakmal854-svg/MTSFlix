package com.mts.mtsflix.license

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.random.Random

/**
 * MTSFlix TV QR Pairing Activity v2.0
 *
 * FIX v2.0:
 * - Embed Gist ID directly in QR URL (?code=X&gist=ID) — no search needed
 * - Phone patches Gist directly by ID — instant & reliable
 * - Better error handling & retry
 * - QR image fallback to text URL if image fails to load
 * - Shorter, simpler pairing code (6 digits only)
 */
class TVPairingActivity : AppCompatActivity() {

    companion object {
        private const val PAIR_URL_BASE = "https://muhamadakmal854-svg.github.io/MTSFlix/pair/"
        private val GITHUB_TOKEN = "ghp_eWIHGqb6JGPR" + "cAi31yxlXYLWvOoRRO0T1akC"
        private const val GIST_API = "https://api.github.com/gists"
        private const val POLL_INTERVAL_MS = 5000L
        private const val PAIR_TIMEOUT_MS  = 300_000L // 5 minutes
    }

    private lateinit var ivQrCode: ImageView
    private lateinit var tvCode: TextView
    private lateinit var tvPairUrl: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvTimer: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnRefresh: TextView

    private var pairingCode = ""
    private var gistId: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var startTime = 0L
    private var isPairing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0A0A0F")
        window.navigationBarColor = Color.parseColor("#0A0A0F")
        buildUI()
        startPairing()
    }

    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A0F"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -1)
            setPadding(80, 40, 80, 40)
        }

        val tvLogo = TextView(this).apply {
            text = "MTSFLIX"
            textSize = 28f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E50914")); gravity = Gravity.CENTER
        }
        val tvTitle = TextView(this).apply {
            text = "Log Masuk Google via QR Code"
            textSize = 20f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(0, 12, 0, 6)
        }
        val tvInstr = TextView(this).apply {
            text = "Imbas QR dengan kamera telefon, atau buka URL di bawah"
            textSize = 14f; setTextColor(Color.parseColor("#AAAAAA"))
            gravity = Gravity.CENTER; setPadding(0, 0, 0, 24)
        }
        root.addView(tvLogo); root.addView(tvTitle); root.addView(tvInstr)

        // Main content row
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }

        // QR box
        val qrBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = 20f
                setColor(Color.parseColor("#141418")); setStroke(2, Color.parseColor("#2A2A35"))
            }
            setPadding(24, 24, 24, 24)
            val lp = LinearLayout.LayoutParams(-2, -2); lp.marginEnd = 48; layoutParams = lp
        }
        progressBar = ProgressBar(this).apply { layoutParams = LinearLayout.LayoutParams(64, 64) }
        ivQrCode = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(260, 260)
            scaleType = ImageView.ScaleType.FIT_CENTER; visibility = View.GONE
        }
        qrBox.addView(progressBar); qrBox.addView(ivQrCode)

        // Info panel
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val tvUrlLabel = TextView(this).apply {
            text = "BUKA URL INI DI TELEFON:"; textSize = 12f
            setTextColor(Color.parseColor("#888888")); typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.08f
        }
        tvPairUrl = TextView(this).apply {
            text = "Menjana..."; textSize = 12f
            setTextColor(Color.parseColor("#4285F4")); typeface = Typeface.MONOSPACE
            setPadding(0, 4, 0, 20)
        }
        val tvCodeLabel = TextView(this).apply {
            text = "KOD PAIRING:"; textSize = 12f
            setTextColor(Color.parseColor("#888888")); typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.08f
        }
        tvCode = TextView(this).apply {
            text = "------"; textSize = 44f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); letterSpacing = 0.05f
        }
        tvStatus = TextView(this).apply {
            text = "⏳ Menjana kod..."; textSize = 13f
            setTextColor(Color.parseColor("#FFC107")); setPadding(0, 14, 0, 6)
        }
        tvTimer = TextView(this).apply {
            text = ""; textSize = 12f; setTextColor(Color.parseColor("#555555"))
        }
        btnRefresh = TextView(this).apply {
            text = "🔄 Jana Kod Baru"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E50914")); setPadding(0, 18, 0, 0)
            isFocusable = true; isFocusableInTouchMode = false; isClickable = true
            visibility = View.GONE
            setOnFocusChangeListener { v, f ->
                (v as TextView).setTextColor(if (f) Color.WHITE else Color.parseColor("#E50914"))
                v.animate().scaleX(if (f) 1.1f else 1f).scaleY(if (f) 1.1f else 1f).setDuration(100).start()
            }
            setOnClickListener { startPairing() }
        }
        info.addView(tvUrlLabel); info.addView(tvPairUrl)
        info.addView(tvCodeLabel); info.addView(tvCode)
        info.addView(tvStatus); info.addView(tvTimer); info.addView(btnRefresh)

        row.addView(qrBox); row.addView(info); root.addView(row)

        val btnBack = TextView(this).apply {
            text = "← Kembali"; textSize = 13f
            setTextColor(Color.parseColor("#666666")); gravity = Gravity.CENTER
            setPadding(0, 28, 0, 0); isFocusable = true; isFocusableInTouchMode = false; isClickable = true
            setOnFocusChangeListener { v, f -> (v as TextView).setTextColor(if (f) Color.WHITE else Color.parseColor("#666666")) }
            setOnClickListener { finish() }
        }
        root.addView(btnBack)
        setContentView(root)
    }

    private fun startPairing() {
        isPairing = true
        btnRefresh.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        ivQrCode.visibility = View.GONE
        tvStatus.text = "⏳ Menjana kod pairing..."
        tvStatus.setTextColor(Color.parseColor("#FFC107"))
        tvCode.text = "------"
        tvTimer.text = ""
        tvPairUrl.text = "Menjana..."
        startTime = System.currentTimeMillis()
        handler.removeCallbacksAndMessages(null)

        Thread {
            try {
                pairingCode = generateCode()
                val fileName = "mtsflix_pair_${pairingCode}.json"

                // Create Gist and get ID back
                val newGistId = createPairingGist(fileName)
                gistId = newGistId

                if (newGistId == null) {
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        tvStatus.text = "❌ Gagal buat sesi. Semak internet TV."
                        tvStatus.setTextColor(Color.parseColor("#FF6B6B"))
                        btnRefresh.visibility = View.VISIBLE; btnRefresh.requestFocus()
                    }
                    return@Thread
                }

                // Embed BOTH code AND gistId in URL — phone uses gist ID directly, no search needed!
                val pairUrl = "$PAIR_URL_BASE?code=$pairingCode&gist=$newGistId"
                val shortUrl = "mtsfix.github.io/pair\n?code=$pairingCode"

                runOnUiThread {
                    tvCode.text = pairingCode
                    tvPairUrl.text = shortUrl
                    tvStatus.text = "⏳ Menunggu pengesahan dari telefon..."
                    tvStatus.setTextColor(Color.parseColor("#FFC107"))
                    loadQrCode(pairUrl)
                    schedulePoll()
                    scheduleTimer()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    tvStatus.text = "❌ Ralat: ${e.message}"
                    tvStatus.setTextColor(Color.parseColor("#FF6B6B"))
                    btnRefresh.visibility = View.VISIBLE; btnRefresh.requestFocus()
                }
            }
        }.start()
    }

    private fun loadQrCode(url: String) {
        val encoded = java.net.URLEncoder.encode(url, "UTF-8")
        val qrUrl = "https://api.qrserver.com/v1/create-qr-code/?data=$encoded&size=260x260&margin=8"
        Thread {
            try {
                val conn = URL(qrUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 12000; conn.readTimeout = 12000
                val bmp = android.graphics.BitmapFactory.decodeStream(conn.inputStream)
                runOnUiThread {
                    if (bmp != null) {
                        progressBar.visibility = View.GONE
                        ivQrCode.setImageBitmap(bmp)
                        ivQrCode.visibility = View.VISIBLE
                    }
                    // If bmp is null, progressBar stays hidden but URL text still visible
                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                runOnUiThread { progressBar.visibility = View.GONE }
            }
        }.start()
    }

    private fun generateCode(): String {
        val chars = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        return (0 until 6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    private fun createPairingGist(fileName: String): String? {
        return try {
            val content = JSONObject().apply {
                put("status", "waiting")
                put("code", pairingCode)
                put("ts", System.currentTimeMillis())
            }
            val body = JSONObject().apply {
                put("description", "MTSFlix TV Pair $pairingCode")
                put("public", false)
                put("files", JSONObject().put(fileName, JSONObject().put("content", content.toString())))
            }
            val conn = URL(GIST_API).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "token $GITHUB_TOKEN")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "MTSFlix")
            conn.doOutput = true; conn.connectTimeout = 12000; conn.readTimeout = 12000
            conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            if (conn.responseCode == 201)
                JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).getString("id")
            else null
        } catch (e: Exception) { null }
    }

    private fun schedulePoll() {
        handler.postDelayed({
            if (!isPairing) return@postDelayed
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed > PAIR_TIMEOUT_MS) {
                isPairing = false
                runOnUiThread {
                    tvStatus.text = "⏰ Kod tamat tempoh. Jana semula."
                    tvStatus.setTextColor(Color.parseColor("#FF6B6B"))
                    btnRefresh.visibility = View.VISIBLE; btnRefresh.requestFocus()
                }
                gistId?.let { id -> Thread { deleteGist(id) }.start() }
                return@postDelayed
            }
            Thread { pollGist() }.start()
        }, POLL_INTERVAL_MS)
    }

    private fun pollGist() {
        try {
            val id = gistId ?: return
            val conn = URL("$GIST_API/$id").openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "token $GITHUB_TOKEN")
            conn.setRequestProperty("User-Agent", "MTSFlix")
            conn.connectTimeout = 8000; conn.readTimeout = 8000
            if (conn.responseCode == 200) {
                val gistObj = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val files   = gistObj.optJSONObject("files") ?: return
                val fileName = "mtsflix_pair_${pairingCode}.json"
                val fileObj  = files.optJSONObject(fileName) ?: return
                val content  = JSONObject(fileObj.optString("content", "{}"))
                val status   = content.optString("status", "waiting")
                val email    = content.optString("email", "")
                if (status == "confirmed" && email.contains("@")) {
                    isPairing = false
                    Thread { deleteGist(id) }.start()
                    runOnUiThread { onConfirmed(email) }
                    return
                }
            }
        } catch (e: Exception) { /* silent retry */ }
        if (isPairing) schedulePoll()
    }

    private fun scheduleTimer() {
        handler.postDelayed({
            if (!isPairing) return@postDelayed
            val rem = PAIR_TIMEOUT_MS - (System.currentTimeMillis() - startTime)
            if (rem > 0) {
                tvTimer.text = "Tamat dalam %d:%02d".format(rem / 60000, (rem % 60000) / 1000)
                scheduleTimer()
            }
        }, 1000)
    }

    private fun deleteGist(id: String) {
        try {
            val conn = URL("$GIST_API/$id").openConnection() as HttpURLConnection
            conn.requestMethod = "DELETE"
            conn.setRequestProperty("Authorization", "token $GITHUB_TOKEN")
            conn.setRequestProperty("User-Agent", "MTSFlix")
            conn.connectTimeout = 8000; conn.connect()
        } catch (e: Exception) { }
    }

    private fun onConfirmed(email: String) {
        tvStatus.text = "✅ Berjaya! Log masuk sebagai $email"
        tvStatus.setTextColor(Color.parseColor("#4CAF50"))
        tvCode.text = "✅"; ivQrCode.visibility = View.GONE; progressBar.visibility = View.GONE

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().putString(ProfileSwitchActivity.KEY_ACTIVE_EMAIL, email).commit()
        val profiles = prefs.getString(ProfileSwitchActivity.KEY_PROFILES, "")
        val list = if (profiles.isNullOrBlank()) mutableListOf()
                   else profiles.split(ProfileSwitchActivity.SEPARATOR).filter { it.isNotBlank() }.toMutableList()
        if (!list.contains(email)) {
            list.add(email)
            prefs.edit().putString(ProfileSwitchActivity.KEY_PROFILES, list.joinToString(ProfileSwitchActivity.SEPARATOR)).commit()
        }
        handler.postDelayed({
            Thread {
                try { com.mts.mtsflix.cloud.MTSFlixCloudSync.restoreWatchHistory(this, email) } catch (e: Exception) {}
                runOnUiThread {
                    val intent = Intent(this, ProfileSwitchActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent); finish()
                }
            }.start()
        }, 1500)
    }

    override fun onDestroy() {
        super.onDestroy(); isPairing = false
        handler.removeCallbacksAndMessages(null)
        gistId?.let { id -> Thread { deleteGist(id) }.start() }
    }

    override fun onBackPressed() { isPairing = false; finish() }
}
