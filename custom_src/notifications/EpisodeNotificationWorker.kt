package com.mts.mtsflix.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * MTSFlix Episode Notification Worker
 * Checks Firebase Firestore every 12 hours for new episodes in user's watchlist
 * and sends a local notification when found.
 */
class EpisodeNotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "MTSFlix:EpisodeWorker"
        const val CHANNEL_ID = "mtsflix_episodes"
        const val CHANNEL_NAME = "Episod Baru"
        const val CHANNEL_DESC = "Pemberitahuan apabila ada episod baru dalam senarai tontonan anda"
        const val WORK_NAME = "mtsflix_episode_tracker"
        
        // ─── Schedule Worker ──────────────────────────────────────────────
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<EpisodeNotificationWorker>(
                repeatInterval = 12, TimeUnit.HOURS,
                flexTimeInterval = 30, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
                .addTag("mtsflix")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.i(TAG, "✅ Episode tracker scheduled (every 12h)")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "🛑 Episode tracker cancelled")
        }

        // ─── Notification Channel ─────────────────────────────────────────
        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = CHANNEL_DESC
                    enableVibration(true)
                }
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(channel)
                Log.d(TAG, "Notification channel created")
            }
        }
    }

    // ─── Worker Logic ─────────────────────────────────────────────────────────

    override suspend fun doWork(): Result {
        Log.i(TAG, "🔍 Checking for new episodes...")
        return try {
            val count = checkForNewEpisodes()
            Log.i(TAG, "✅ Check complete — $count notifications sent")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Episode check failed: ${e.message}")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun checkForNewEpisodes(): Int {
        val user = FirebaseAuth.getInstance().currentUser
            ?: run {
                Log.d(TAG, "User not signed in, skipping check")
                return 0
            }

        val db = FirebaseFirestore.getInstance()
        var notificationCount = 0

        try {
            val watchlist = db.collection("users")
                .document(user.uid)
                .collection("watchlist")
                .whereEqualTo("ongoing", true)
                .get()
                .await()

            for (doc in watchlist.documents) {
                val title = doc.getString("title") ?: continue
                val lastNotifiedEp = doc.getLong("lastNotifiedEpisode") ?: 0L
                val currentEpisode = doc.getLong("currentEpisode") ?: 0L

                if (currentEpisode > lastNotifiedEp) {
                    val newEpsCount = currentEpisode - lastNotifiedEp
                    sendEpisodeNotification(title, newEpsCount.toInt(), currentEpisode.toInt())
                    
                    // Update last notified episode count
                    doc.reference.update("lastNotifiedEpisode", currentEpisode)
                    notificationCount++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firestore error: ${e.message}")
            throw e
        }

        return notificationCount
    }

    // ─── Send Notification ────────────────────────────────────────────────────

    private fun sendEpisodeNotification(title: String, newEpsCount: Int, latestEp: Int) {
        createNotificationChannel(context)

        // Try to open the app when notification is tapped
        val intent = try {
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("search_query", title)
            } ?: Intent()
        } catch (e: Exception) {
            Intent()
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            title.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentText = when {
            newEpsCount == 1 -> "Episod $latestEp sudah tersedia! Tonton sekarang 🎬"
            newEpsCount <= 3 -> "$newEpsCount episod baru! Terkini: Episod $latestEp 🎬"
            else -> "$newEpsCount episod baru tersedia! Episod $latestEp terkini 🎬"
        }

        // Use a generic drawable ID — CloudStream has film/video icons
        val iconRes = context.resources.getIdentifier("ic_movie", "drawable", context.packageName)
            .takeIf { it != 0 }
            ?: android.R.drawable.ic_media_play

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle("🆕 $title")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(contentText)
                .setBigContentTitle("🆕 $title — Episod Baru!"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup("mtsflix_episodes")
            .build()

        val notificationManager = NotificationManagerCompat.from(context)
        
        // Check permission on Android 13+
        val canNotify = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        if (canNotify) {
            notificationManager.notify(title.hashCode(), notification)
            Log.i(TAG, "📬 Notification sent: $title ($newEpsCount new eps)")
        } else {
            Log.w(TAG, "⚠️ POST_NOTIFICATIONS permission not granted")
        }
    }
}
