package com.mts.mtsflix.watchlist

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * MTSFlix Watchlist Notification Worker v1.1.5
 * Background job yang menyemak episod baru setiap 6 jam dan menghantar notifikasi.
 */
class WatchlistNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val WORK_NAME       = "mtsflix_watchlist_check"
        private const val CHANNEL_ID      = "mtsflix_watchlist_channel"
        private const val CHANNEL_NAME    = "MTSFlix Episod Baru"
        private const val CHECK_INTERVAL_HOURS = 6L

        fun schedulePeriodicCheck(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<WatchlistNotificationWorker>(
                CHECK_INTERVAL_HOURS, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancelPeriodicCheck(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        private fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifikasi episod baru dari senarai tonton anda"
                }
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(channel)
            }
        }
    }

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                createNotificationChannel(applicationContext)
                val watchlist = WatchlistManager.getWatchlist(applicationContext)
                if (watchlist.isEmpty()) return@withContext Result.success()

                watchlist.forEach { item ->
                    try {
                        val api = APIHolder.getApiFromNameNull(item.apiName) ?: return@forEach
                        val loadResponse = api.load(item.url)
                        if (loadResponse is TvSeriesLoadResponse) {
                            val newEpCount = loadResponse.episodes.size
                            val oldEpCount = item.lastKnownEpisodeCount
                            if (newEpCount > oldEpCount && oldEpCount > 0) {
                                val newEps = newEpCount - oldEpCount
                                sendNewEpisodeNotification(item, newEps, newEpCount)
                            }
                            if (newEpCount != oldEpCount) {
                                WatchlistManager.updateLastEpisodeCount(applicationContext, item.url, newEpCount)
                            }
                        }
                    } catch (e: Exception) { /* skip item silently */ }
                }
                Result.success()
            } catch (e: Exception) {
                Result.retry()
            }
        }
    }

    private fun sendNewEpisodeNotification(
        item: WatchlistManager.WatchlistItem,
        newEpisodes: Int,
        totalEpisodes: Int
    ) {
        val launchIntent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP) }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext, item.url.hashCode(), launchIntent ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notifBody = if (newEpisodes == 1)
            "1 episod baru tersedia! (Jumlah: $totalEpisodes episod)"
        else
            "$newEpisodes episod baru tersedia! (Jumlah: $totalEpisodes episod)"

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("🔔 ${item.name}")
            .setContentText(notifBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${item.name}\n$notifBody\n\nTekan untuk menonton sekarang!"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(item.url.hashCode(), notification)
    }
}
