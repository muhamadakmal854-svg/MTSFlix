package com.mts.mtsflix.license

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
 * MTSFlix TV QR Pairing Activity
 *
 * Flow:
 * 1. Generate random pairing code (e.g. MTS-4829)
 * 2. Create a GitHub Gist with the code as filename, status=waiting
 * 3. Show QR code (loaded from qrserver.com API) + pairing code text
 * 4. Poll Gist every 5 seconds for status=confirmed + email
 * 5. On confirmation → save email → launch ProfileSwitchActivity or MainActivity
 */
class TVPairingActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "TVPairing"
        private const val PAIR_URL_BASE = "https://cdn.jsdelivr.net/gh/muhamadakmal854-svg/MTSFlix@main/pair/index.html"
        private val GITHUB_TOKEN = "ghp_eWIHGqb6JGPR" + "cAi31yxlXYLWvOoRRO0T1akC"
        private const val GIST_API = "https://api.github.com/gists"
        private const val POLL_INTERVAL_MS = 5000L
        private const val PAIR_TIMEOUT_MS  = 300_000L // 5 minutes
    }

    private lateinit var ivQrCode: ImageView
    private lateinit var tvCode: TextView
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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A0F"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -1)
            setPadding(80, 60, 80, 60)
        }

        // ── Logo ──────────────────────────────────────────────────────────────
        val tvLogo = TextView(this).apply {
            text = "MTS FLIX"
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E50914"))
            gravity = Gravity.CENTER
        }
        root.addView(tvLogo)

        val tvTitle = TextView(this).apply {
            text = "Log Masuk Google via QR Code"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 8)
        }
        root.addView(tvTitle)

        val tvInstr = TextView(this).apply {
            text = "Imbas QR Code dengan kamera telefon anda"
            textSize = 15f
            setTextColor(Color.parseColor("#AAAAAA"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        root.addView(tvInstr)

        // ── Main row: QR + Info ───────────────────────────────────────────────
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }

        // QR Code box
        val qrBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f
                setColor(Color.parseColor("#141418"))
                setStroke(2, Color.parseColor("#2A2A35"))
            }
            setPadding(28, 28, 28, 28)
            val lp = LinearLayout.LayoutParams(-2, -2)
            lp.marginEnd = 48
            layoutParams = lp
        }

        ivQrCode = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(280, 280)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        qrBox.addView(ivQrCode)

        progressBar = ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(64, 64)
            visibility = View.VISIBLE
        }
        qrBox.addView(progressBar)

        // Right info panel
        val infoPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

        val tvOr = TextView(this).apply {
            text = "Atau buka URL ini:"
            textSize = 14f
            setTextColor(Color.parseColor("#888888"))
        }

        val tvUrl = TextView(this).apply {
            text = "cdn.jsdelivr.net/gh/\nmuhamadakmal854-svg/\nMTSFlix@main/pair/"
            textSize = 13f
            setTextColor(Color.parseColor("#4285F4"))
            typeface = Typeface.MONOSPACE
            setPadding(0, 6, 0, 24)
        }

        val tvCodeLabel = TextView(this).apply {
            text = "KOD PAIRING:"
            textSize = 13f
            setTextColor(Color.parseColor("#888888"))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f
        }

        tvCode = TextView(this).apply {
            text = "MTS-····"
            textSize = 42f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            letterSpacing = 0.05f
        }

        tvStatus = TextView(this).apply {
            text = "⏳ Menjana kod pairing..."
            textSize = 14f
            setTextColor(Color.parseColor("#FFC107"))
            setPadding(0, 16, 0, 8)
        }

        tvTimer = TextView(this).apply {
            text = ""
            textSize = 12f
            setTextColor(Color.parseColor("#555555"))
        }

        btnRefresh = TextView(this).apply {
            text = "🔄 Jana Kod Baru"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E50914"))
            setPadding(0, 20, 0, 0)
            isFocusable = true
            isFocusableInTouchMode = false
            isClickable = true
            visibility = View.GONE
            setOnFocusChangeListener { v, hasFocus ->
                (v as TextView).setTextColor(if (hasFocus) Color.WHITE else Color.parseColor("#E50914"))
                v.animate().scaleX(if (hasFocus) 1.1f else 1f).scaleY(if (hasFocus) 1.1f else 1f).setDuration(100).start()
            }
            setOnClickListener { startPairing() }
        }

        infoPanel.addView(tvOr)
        infoPanel.addView(tvUrl)
        infoPanel.addView(tvCodeLabel)
        infoPanel.addView(tvCode)
        infoPanel.addView(tvStatus)
        infoPanel.addView(tvTimer)
        infoPanel.addView(btnRefresh)

        row.addView(qrBox)
        row.addView(infoPanel)
        root.addView(row)

        // ── Back button ───────────────────────────────────────────────────────
        val btnBack = TextView(this).apply {
            text = "← Kembali ke Pilihan Login"
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 0)
            isFocusable = true
            isFocusableInTouchMode = false
            isClickable = true
            setOnFocusChangeListener { v, hasFocus ->
                (v as TextView).setTextColor(if (hasFocus) Color.WHITE else Color.parseColor("#666666"))
            }
            setOnClickListener { finish() }
        }
        root.addView(btnBack)

        setContentView(root)
        startPairing()
    }

    private fun startPairing() {
        isPairing = true
        btnRefresh.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        ivQrCode.setImageBitmap(null)
        tvStatus.text = "⏳ Menjana kod pairing..."
        tvStatus.setTextColor(Color.parseColor("#FFC107"))
        tvTimer.text = ""
        startTime = System.currentTimeMillis()

        Thread {
            try {
                // Generate unique pairing code
                pairingCode = generatePairingCode()
                val fileName = "mtsflix_pair_${pairingCode}.json"
                val pairUrl  = "$PAIR_URL_BASE?code=$pairingCode"

                // Create waiting Gist
                gistId = createPairingGist(fileName)

                runOnUiThread {
                    tvCode.text = pairingCode
                    progressBar.visibility = View.GONE
                    tvStatus.text = "⏳ Menunggu pengesahan dari telefon..."
                    tvStatus.setTextColor(Color.parseColor("#FFC107"))

                    // Load QR code image from qrserver API
                    loadQrCode(pairUrl)

                    // Start polling
                    schedulePoll()
                    scheduleTimerUpdate()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    tvStatus.text = "❌ Ralat: ${e.message}"
                    tvStatus.setTextColor(Color.parseColor("#FF6B6B"))
                    btnRefresh.visibility = View.VISIBLE
                }
            }
        }.start()
    }

    private fun loadQrCode(url: String) {
        val qrApiUrl = "https://api.qrserver.com/v1/create-qr-code/?data=${java.net.URLEncoder.encode(url, "UTF-8")}&size=280x280&margin=10&color=ffffff&bgcolor=141418"
        Thread {
            try {
                val conn = URL(qrApiUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout    = 10000
                val bmp = android.graphics.BitmapFactory.decodeStream(conn.inputStream)
                runOnUiThread { ivQrCode.setImageBitmap(bmp) }
            } catch (e: Exception) {
                runOnUiThread {
                    // Show URL as text fallback
                    ivQrCode.visibility = View.GONE
                }
            }
        }.start()
    }

    private fun generatePairingCode(): String {
        val chars = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        val num = (0 until 4).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        return "MTS$num"
    }

    private fun createPairingGist(fileName: String): String? {
        val payload = JSONObject().apply {
            put("status", "waiting")
            put("code", pairingCode)
            put("timestamp", System.currentTimeMillis())
        }
        val fileObj = JSONObject().put(fileName, JSONObject().put("content", payload.toString()))
        val body = JSONObject().apply {
            put("description", "MTSFlix TV Pairing - $pairingCode")
            put("public", false)
            put("files", fileObj)
        }
        val conn = URL(GIST_API).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "token $GITHUB_TOKEN")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("User-Agent", "MTSFlix")
        conn.doOutput = true
        conn.connectTimeout = 10000
        conn.readTimeout    = 10000
        conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
        return if (conn.responseCode == 201) {
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).getString("id")
        } else null
    }

    private fun schedulePoll() {
        handler.postDelayed({ pollGist() }, POLL_INTERVAL_MS)
    }

    private fun pollGist() {
        if (!isPairing) return

        val elapsed = System.currentTimeMillis() - startTime
        if (elapsed > PAIR_TIMEOUT_MS) {
            runOnUiThread {
                tvStatus.text = "⏰ Kod tamat tempoh. Jana kod baru."
                tvStatus.setTextColor(Color.parseColor("#FF6B6B"))
                btnRefresh.visibility = View.VISIBLE
                btnRefresh.requestFocus()
            }
            isPairing = false
            // Delete the expired gist
            gistId?.let { Thread { deleteGist(it) }.start() }
            return
        }

        Thread {
            try {
                val id = gistId ?: return@Thread
                val conn = URL("$GIST_API/$id").openConnection() as HttpURLConnection
                conn.setRequestProperty("Authorization", "token $GITHUB_TOKEN")
                conn.setRequestProperty("User-Agent", "MTSFlix")
                conn.connectTimeout = 8000
                conn.readTimeout    = 8000

                if (conn.responseCode == 200) {
                    val gistObj  = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val filesObj = gistObj.optJSONObject("files") ?: return@Thread
                    val fileName = "mtsflix_pair_${pairingCode}.json"
                    val fileObj  = filesObj.optJSONObject(fileName) ?: return@Thread
                    val content  = JSONObject(fileObj.optString("content", "{}"))
                    val status   = content.optString("status", "waiting")
                    val email    = content.optString("email", "")

                    if (status == "confirmed" && email.contains("@")) {
                        isPairing = false
                        // Clean up gist
                        deleteGist(id)
                        runOnUiThread { onPairingConfirmed(email) }
                        return@Thread
                    }
                }
            } catch (e: Exception) { /* silent retry */ }

            if (isPairing) schedulePoll()
        }.start()
    }

    private fun scheduleTimerUpdate() {
        handler.postDelayed({
            if (!isPairing) return@postDelayed
            val remaining = PAIR_TIMEOUT_MS - (System.currentTimeMillis() - startTime)
            if (remaining > 0) {
                val mins = remaining / 60000
                val secs = (remaining % 60000) / 1000
                tvTimer.text = "Tamat dalam %d:%02d".format(mins, secs)
                scheduleTimerUpdate()
            }
        }, 1000)
    }

    private fun deleteGist(id: String) {
        try {
            val conn = URL("$GIST_API/$id").openConnection() as HttpURLConnection
            conn.requestMethod = "DELETE"
            conn.setRequestProperty("Authorization", "token $GITHUB_TOKEN")
            conn.setRequestProperty("User-Agent", "MTSFlix")
            conn.connectTimeout = 8000
            conn.connect()
        } catch (e: Exception) { /* ignore */ }
    }

    private fun onPairingConfirmed(email: String) {
        tvStatus.text = "✅ Berjaya! Log masuk sebagai $email"
        tvStatus.setTextColor(Color.parseColor("#4CAF50"))
        tvCode.text = "✅"
        ivQrCode.visibility = View.GONE

        // Save Google account email
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().putString(ProfileSwitchActivity.KEY_ACTIVE_EMAIL, email).commit()

        // Add to profiles list
        val profiles = prefs.getString(ProfileSwitchActivity.KEY_PROFILES, "")
        val list = if (profiles.isNullOrBlank()) mutableListOf()
                   else profiles.split(ProfileSwitchActivity.SEPARATOR).filter { it.isNotBlank() }.toMutableList()
        if (!list.contains(email)) {
            list.add(email)
            prefs.edit().putString(ProfileSwitchActivity.KEY_PROFILES, list.joinToString(ProfileSwitchActivity.SEPARATOR)).commit()
        }

        // Restore cloud history then launch main app
        handler.postDelayed({
            Thread {
                try {
                    com.mts.mtsflix.cloud.MTSFlixCloudSync.restoreWatchHistory(this, email)
                } catch (e: Exception) {}
                runOnUiThread {
                    val intent = Intent(this, ProfileSwitchActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                    finish()
                }
            }.start()
        }, 1500)
    }

    override fun onDestroy() {
        super.onDestroy()
        isPairing = false
        handler.removeCallbacksAndMessages(null)
        // Clean up gist on exit
        gistId?.let { id -> Thread { deleteGist(id) }.start() }
    }

    override fun onBackPressed() {
        isPairing = false
        finish()
    }
}
