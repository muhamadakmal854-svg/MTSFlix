package com.mts.mtsflix.license

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI

/**
 * MTSFlix Provider Lock Management Activity v1.1.4
 * Paparan senarai Provider dengan pengesahan PIN Lama & Confirmation PIN Baru.
 */
class ProviderLockManageActivity : AppCompatActivity() {

    companion object {
        private const val REQ_VERIFY_OLD_PIN = 2001
        private const val REQ_SET_NEW_PIN = 2002
    }

    private lateinit var listView: ListView
    private lateinit var tvStatus: TextView
    private lateinit var btnChangePin: Button
    private var isUnlockedForManagement = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0A0A0F")
        window.navigationBarColor = Color.parseColor("#0A0A0F")

        // Jika PIN sudah wujud dan belum disahkan dalam sesi pengurusan ini, minta PIN Lama dahulu!
        if (ProviderLockManager.hasCustomPin(this) && !isUnlockedForManagement) {
            val intent = Intent(this, ProviderLockPinActivity::class.java).apply {
                putExtra(ProviderLockPinActivity.EXTRA_MODE, ProviderLockPinActivity.MODE_VERIFY_OLD_PIN)
            }
            startActivityForResult(intent, REQ_VERIFY_OLD_PIN)
        } else {
            isUnlockedForManagement = true
        }

        buildUI()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A0F"))
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = LinearLayout.LayoutParams(-1, -1)
        }

        // Header Title
        val header = TextView(this).apply {
            text = "🔒 Kunci Provider (PIN Lock)"; textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(6))
        }
        root.addView(header)

        tvStatus = TextView(this).apply {
            text = if (ProviderLockManager.hasCustomPin(this@ProviderLockManageActivity))
                "📌 Status PIN: Terkustom (PIN Kustom Aktif)"
            else
                "📌 Status PIN: Lalai (0000)"
            textSize = 13f
            setTextColor(Color.parseColor("#AAAAAA"))
            setPadding(0, 0, 0, dp(16))
        }
        root.addView(tvStatus)

        // Button Change PIN
        btnChangePin = Button(this).apply {
            text = "🔑 Set / Tukar PIN (4 Digit)"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#E50914"))
            }
            val lp = LinearLayout.LayoutParams(-1, dp(48))
            lp.bottomMargin = dp(20)
            layoutParams = lp

            isFocusable = true; isFocusableInTouchMode = false
            setOnFocusChangeListener { v, f ->
                v.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(8).toFloat()
                    setColor(if (f) Color.parseColor("#FF2E38") else Color.parseColor("#E50914"))
                }
            }
            setOnClickListener {
                if (ProviderLockManager.hasCustomPin(this@ProviderLockManageActivity) && !isUnlockedForManagement) {
                    val verifyIntent = Intent(this@ProviderLockManageActivity, ProviderLockPinActivity::class.java).apply {
                        putExtra(ProviderLockPinActivity.EXTRA_MODE, ProviderLockPinActivity.MODE_VERIFY_OLD_PIN)
                    }
                    startActivityForResult(verifyIntent, REQ_VERIFY_OLD_PIN)
                } else {
                    openSetPinScreen()
                }
            }
        }
        root.addView(btnChangePin)

        // Subtitle Provider List
        val subTitle = TextView(this).apply {
            text = "Pilih Provider yang hendak dikunci dengan PIN:"; textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#DDDDDD"))
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(subTitle)

        // List of All Installed Providers
        val providers: List<MainAPI> = APIHolder.apis.sortedBy { it.name }

        listView = ListView(this).apply {
            divider = GradientDrawable().apply { setColor(Color.parseColor("#222222")); setSize(0, dp(1)) }
            dividerHeight = dp(1)
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }

        val adapter = object : ArrayAdapter<MainAPI>(this, 0, providers) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val provider = getItem(position) ?: return View(context)

                val itemLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(14), dp(12), dp(14))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#141418"))
                        cornerRadius = dp(8).toFloat()
                    }
                    isFocusable = true; isFocusableInTouchMode = false
                    setOnFocusChangeListener { v, f ->
                        v.background = GradientDrawable().apply {
                            setColor(if (f) Color.parseColor("#252530") else Color.parseColor("#141418"))
                            cornerRadius = dp(8).toFloat()
                        }
                    }
                }

                val titleTv = TextView(context).apply {
                    text = provider.name
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                }
                itemLayout.addView(titleTv)

                val lockSwitch = Switch(context).apply {
                    isChecked = ProviderLockManager.isProviderLocked(context, provider.name)
                    isFocusable = false; isClickable = false
                }
                itemLayout.addView(lockSwitch)

                itemLayout.setOnClickListener {
                    val currentLocked = ProviderLockManager.isProviderLocked(context, provider.name)
                    val newLocked = !currentLocked
                    ProviderLockManager.setProviderLocked(context, provider.name, newLocked)
                    lockSwitch.isChecked = newLocked
                    val msg = if (newLocked) "🔒 Provider ${provider.name} Dikunci!" else "🔓 Provider ${provider.name} Di-unlock"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }

                return itemLayout
            }
        }

        listView.adapter = adapter
        root.addView(listView)

        setContentView(root)
    }

    private fun openSetPinScreen() {
        val intent = Intent(this, ProviderLockPinActivity::class.java).apply {
            putExtra(ProviderLockPinActivity.EXTRA_MODE, ProviderLockPinActivity.MODE_SET_PIN)
        }
        startActivityForResult(intent, REQ_SET_NEW_PIN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VERIFY_OLD_PIN) {
            if (resultCode == RESULT_OK) {
                isUnlockedForManagement = true
            } else {
                Toast.makeText(this, "❌ Pengesahan PIN Lama Gagal", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else if (requestCode == REQ_SET_NEW_PIN && resultCode == RESULT_OK) {
            tvStatus.text = "📌 Status PIN: Terkustom (PIN Kustom Aktif)"
        }
    }
}
