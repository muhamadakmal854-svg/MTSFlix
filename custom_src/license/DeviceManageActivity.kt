package com.mts.mtsflix.license

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.mts.mtsflix.theme.MTSFlixThemeManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * MTSFlix Device Management Activity v1.1.5
 * Paparan senarai peranti berdaftar dan kawalan pencabutan akses (D-pad / remote TV friendly).
 */
class DeviceManageActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private var email: String = ""
    private val accent get() = MTSFlixThemeManager.getAccentColor(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MTSFlixThemeManager.applyToWindow(this)
        email = PreferenceManager.getDefaultSharedPreferences(this)
            .getString(ProfileSwitchActivity.KEY_ACTIVE_EMAIL, "") ?: ""
        buildUI()
        if (email.isNotBlank()) loadDevices()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A0F"))
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        val tvIcon = TextView(this).apply { text = "🛡️"; textSize = 34f; gravity = Gravity.CENTER }
        root.addView(tvIcon)

        val tvTitle = TextView(this).apply {
            text = "Pengurusan Peranti"; textSize = 22f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(4))
        }
        root.addView(tvTitle)

        tvStatus = TextView(this).apply {
            text = if (email.isNotBlank()) "Akaun: $email" else "Tiada akaun Google daftar masuk"
            textSize = 13f; setTextColor(Color.parseColor("#AAAAAA"))
            gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(16))
        }
        root.addView(tvStatus)

        progressBar = ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(-2, -2).also { it.gravity = Gravity.CENTER }
        }
        root.addView(progressBar)

        listView = ListView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            divider = null; dividerHeight = dp(8)
        }
        root.addView(listView)

        setContentView(root)
    }

    private fun loadDevices() {
        progressBar.visibility = View.VISIBLE
        Thread {
            val devices = DeviceManager.getRegisteredDevices(this, email)
            runOnUiThread {
                progressBar.visibility = View.GONE
                if (devices.isEmpty()) {
                    tvStatus.text = "Tiada peranti berdaftar ditemui"
                    return@runOnUiThread
                }
                tvStatus.text = "${devices.size} peranti berdaftar dengan akaun ini"
                setupAdapter(devices)
            }
        }.start()
    }

    private fun setupAdapter(devices: List<DeviceManager.DeviceInfo>) {
        val myDeviceId = DeviceManager.getOrCreateDeviceId(this)
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

        val adapter = object : ArrayAdapter<DeviceManager.DeviceInfo>(this, 0, devices) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val device = getItem(position) ?: return View(context)
                val isThisDevice = device.id == myDeviceId

                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(10).toFloat()
                        setColor(if (isThisDevice) Color.parseColor("#181828") else Color.parseColor("#141418"))
                        if (isThisDevice) setStroke(dp(2), accent)
                    }
                    isFocusable = true; isFocusableInTouchMode = false
                    setOnFocusChangeListener { v, f ->
                        v.background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE; cornerRadius = dp(10).toFloat()
                            setColor(if (f) Color.parseColor("#252535") else if (isThisDevice) Color.parseColor("#181828") else Color.parseColor("#141418"))
                            if (isThisDevice || f) setStroke(dp(2), accent)
                        }
                    }
                }

                val headerRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                }
                val tvDeviceName = TextView(context).apply {
                    text = (if (isThisDevice) "📱 " else "📺 ") + device.name
                    textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                }
                headerRow.addView(tvDeviceName)

                if (isThisDevice) {
                    val tvBadge = TextView(context).apply {
                        text = "Peranti Ini"; textSize = 10f
                        setTextColor(Color.WHITE); setPadding(dp(8), dp(2), dp(8), dp(2))
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE; cornerRadius = dp(10).toFloat()
                            setColor(accent)
                        }
                    }
                    headerRow.addView(tvBadge)
                }
                card.addView(headerRow)

                val tvModel = TextView(context).apply {
                    text = "Model: ${device.model} • Log masuk: ${dateFormat.format(Date(device.registeredAt))}"
                    textSize = 11f; setTextColor(Color.parseColor("#888888")); setPadding(0, dp(4), 0, 0)
                }
                card.addView(tvModel)

                val tvLastSeen = TextView(context).apply {
                    text = "Terakhir aktif: ${dateFormat.format(Date(device.lastSeen))}"
                    textSize = 11f; setTextColor(Color.parseColor("#666666"))
                }
                card.addView(tvLastSeen)

                if (!isThisDevice) {
                    val btnRevoke = TextView(context).apply {
                        text = "🚫 Log Keluar Peranti Ini"
                        textSize = 13f; typeface = Typeface.DEFAULT_BOLD
                        setTextColor(Color.WHITE); gravity = Gravity.CENTER
                        val lp = LinearLayout.LayoutParams(-1, dp(38)); lp.topMargin = dp(10); layoutParams = lp
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE; cornerRadius = dp(8).toFloat()
                            setColor(Color.parseColor("#AA1010"))
                        }
                        isFocusable = true; isFocusableInTouchMode = false
                        setOnFocusChangeListener { v, f ->
                            v.background = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(8).toFloat()
                                setColor(if (f) Color.parseColor("#CC2020") else Color.parseColor("#AA1010"))
                            }
                        }
                        setOnClickListener {
                            DeviceManager.revokeDevice(context, device.id, email)
                            Toast.makeText(context, "🚫 Peranti ${device.name} dilog keluar!", Toast.LENGTH_SHORT).show()
                            loadDevices()
                        }
                        setOnKeyListener { _, k, e ->
                            if (e.action == KeyEvent.ACTION_DOWN && (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER)) {
                                performClick(); true
                            } else false
                        }
                    }
                    card.addView(btnRevoke)
                }

                return card
            }
        }
        listView.adapter = adapter
    }
}
