package com.mts.mtsflix.player

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * MTSFlix Auto-Skip Manager v1.1.5
 * Mengurus logik Auto-Skip Intro dan Auto-Main Episod Seterusnya.
 */
object AutoSkipManager {

    // SharedPreferences keys
    const val PREF_AUTO_SKIP_INTRO = "mtsflix_auto_skip_intro"
    const val PREF_AUTO_NEXT_EP    = "mtsflix_auto_next_ep"

    // Default config values
    const val INTRO_WINDOW_START_MS  = 0L           // Intro mungkin bermula dari sini
    const val INTRO_WINDOW_END_MS    = 120_000L     // Intro tidak lebih 2 minit
    const val INTRO_SKIP_TO_MS       = 90_000L      // Skip ke 1:30 minit (anggaran tamat intro)
    const val SKIP_BUTTON_DELAY_MS   = 3_000L       // Auto-skip selepas butang muncul 3 saat
    const val NEXT_EP_TRIGGER_MS     = 90_000L      // Tunjuk overlay 90 saat sebelum tamat
    const val NEXT_EP_COUNTDOWN_SECS = 10            // Countdown 10 saat sebelum auto-next

    fun isAutoSkipIntroEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREF_AUTO_SKIP_INTRO, true)
    }

    fun isAutoNextEpEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREF_AUTO_NEXT_EP, true)
    }

    /**
     * Semak sama ada butang Skip Intro patut muncul berdasarkan posisi semasa.
     * @param positionMs posisi semasa video dalam ms
     * @param isEpisodeBased sama ada ini siri bukan filem
     */
    fun shouldShowSkipIntro(positionMs: Long, isEpisodeBased: Boolean): Boolean {
        if (!isEpisodeBased) return false
        return positionMs in INTRO_WINDOW_START_MS..INTRO_WINDOW_END_MS
    }

    /**
     * Semak berapa saat lagi sebelum perlu tunjuk overlay Next Episode.
     * @param positionMs posisi semasa dalam ms
     * @param durationMs jumlah durasi video dalam ms
     * @return saat berbaki untuk countdown, atau null jika belum masa
     */
    fun getNextEpCountdownSecs(positionMs: Long, durationMs: Long): Int? {
        if (durationMs <= 0) return null
        val remaining = durationMs - positionMs
        if (remaining in 0..NEXT_EP_TRIGGER_MS) {
            return (remaining / 1000).toInt()
        }
        return null
    }

    /**
     * Kira posisi untuk skip intro (lompat ke INTRO_SKIP_TO_MS atau 20% ke depan)
     */
    fun getSkipToPosition(currentMs: Long, durationMs: Long): Long {
        val skipTo = INTRO_SKIP_TO_MS
        return minOf(skipTo, durationMs - 1000)
    }
}
