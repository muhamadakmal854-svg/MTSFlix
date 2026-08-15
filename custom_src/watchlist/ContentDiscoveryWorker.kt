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
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * MTSFlix Content Discovery Worker v1.1.5
 * Background job yang berjalan setiap 6 jam untuk mengesan:
 *   Track 1 — Episod baru untuk show yang sedang ditonton (🔔 diaktifkan pengguna)
 *   Track 2 — Filem baru ditambah ke provider
 *   Track 3 — Siri TV baru ditambah ke provider
 *   Track 4 — Anime baru (Jepun, OVA) ditambah ke provider
 *   Track 5 — Drama Asia / China baru ditambah ke provider
 */
class ContentDiscoveryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val WORK_NAME          = "mtsflix_content_discovery"
        private const val CHANNEL_ID         = "mtsflix_discovery_channel"
        private const val CHANNEL_NAME       = "MTSFlix — Kandungan & Episod Baru"
        private const val CHECK_INTERVAL_HRS = 6L
        private const val API_TIMEOUT_MS     = 15_000L  // 15 saat timeout per API call
        private const val MAX_ITEMS_PER_CAT  = 25       // Ambil max 25 item per kategori

        fun schedulePeriodicCheck(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<ContentDiscoveryWorker>(
                CHECK_INTERVAL_HRS, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
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
                    description = "Pemberitahuan kandungan baru dan episod baru dari provider MTSFlix"
                }
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .createNotificationChannel(channel)
            }
        }
    }

    private val nm by lazy {
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                createNotificationChannel(applicationContext)
                val disabledProviders = WatchlistManager.getDisabledProviders(applicationContext)

                // Track 1 — New episodes for shows user is watching
                if (ContentDiscoveryManager.isNewEpisodesEnabled(applicationContext)) {
                    checkNewEpisodes(disabledProviders)
                }

                // Tracks 2-5 — New content discovery from provider home pages
                val enabledApis = try {
                    APIHolder.apis.filter { !disabledProviders.contains(it.name) }
                } catch (e: Exception) { emptyList() }

                if (enabledApis.isNotEmpty()) {
                    discoverNewContent(enabledApis)
                }

                Result.success()
            } catch (e: Exception) {
                Result.retry()
            }
        }
    }

    // ── Track 1: New episodes ─────────────────────────────────────────
    private suspend fun checkNewEpisodes(disabledProviders: Set<String>) {
        val enabledUrls = WatchlistManager.getEnabledNotificationUrls(applicationContext)
        if (enabledUrls.isEmpty()) return

        val historyItems = WatchlistManager.getHistoryFromDataStore()
        val toCheck = historyItems.filter {
            enabledUrls.contains(it.url) && !disabledProviders.contains(it.apiName)
        }

        toCheck.forEach { item ->
            try {
                val api = APIHolder.getApiFromNameNull(item.apiName) ?: return@forEach
                val response = withTimeoutOrNull(API_TIMEOUT_MS) { api.load(item.url) }
                    ?: return@forEach

                if (response is TvSeriesLoadResponse) {
                    val currentCount = response.episodes.size
                    val lastKnown = WatchlistManager.getLastEpisodeCount(applicationContext, item.url)

                    if (currentCount > lastKnown) {
                        val newEps = if (lastKnown > 0) currentCount - lastKnown else 0
                        if (newEps > 0) {
                            sendEpisodeNotification(item.name, item.apiName, newEps, currentCount)
                        }
                        WatchlistManager.setLastEpisodeCount(applicationContext, item.url, currentCount)
                    }
                }
            } catch (e: Exception) { /* skip silently */ }
        }
    }

    // ── Tracks 2-5: New content from provider home pages ─────────────
    private suspend fun discoverNewContent(apis: List<com.lagradost.cloudstream3.MainAPI>) {
        apis.forEach { api ->
            try {
                val oldSnapshot = ContentDiscoveryManager.getSnapshot(applicationContext, api.name)
                val fetchedItems = mutableListOf<SearchResponse>()

                // Ambil first 3 kategori dari homepage provider
                val categories = api.mainPage.take(3)
                categories.forEach { cat ->
                    try {
                        val request = MainPageRequest(cat.name, cat.data, cat.horizontalImages)
                        val response = withTimeoutOrNull(API_TIMEOUT_MS) {
                            api.getMainPage(1, request)
                        }
                        response?.items?.forEach { homeList ->
                            fetchedItems.addAll(homeList.list.take(MAX_ITEMS_PER_CAT))
                        }
                    } catch (e: Exception) { /* skip category */ }
                }

                if (fetchedItems.isEmpty()) return@forEach

                // Kesan item baru berbanding snapshot
                val newItems = ContentDiscoveryManager.findNewItems(oldSnapshot, fetchedItems)

                // Simpan snapshot terkini
                val updatedSnapshot = (oldSnapshot + fetchedItems.map { it.url })
                    .take(ContentDiscoveryManager::class.java.getDeclaredField("MAX_SNAP_SIZE")
                        .also { it.isAccessible = true }.getInt(null))
                ContentDiscoveryManager.saveSnapshot(applicationContext, api.name, updatedSnapshot)

                if (newItems.isEmpty()) return@forEach

                // Hantar notifikasi berkumpulan mengikut jenis kandungan
                sendGroupedContentNotifications(api.name, newItems)

            } catch (e: Exception) { /* skip provider */ }
        }
    }

    private fun sendGroupedContentNotifications(apiName: String, newItems: List<SearchResponse>) {
        val ctx = applicationContext

        // Kumpulkan mengikut jenis
        val movieItems = newItems.filter { it.type in ContentDiscoveryManager.MOVIE_TYPES }
        val seriesItems = newItems.filter { it.type in ContentDiscoveryManager.SERIES_TYPES }
        val animeItems = newItems.filter { it.type in ContentDiscoveryManager.ANIME_TYPES }
        val asianItems = newItems.filter { it.type in ContentDiscoveryManager.ASIAN_TYPES }
        val otherItems = newItems.filter {
            it.type == null || it.type == TvType.Others || it.type == TvType.Music
        }

        if (ContentDiscoveryManager.isNewMoviesEnabled(ctx) && movieItems.isNotEmpty())
            sendContentGroupNotification(apiName, "🎬 Filem Baru", movieItems)

        if (ContentDiscoveryManager.isNewSeriesEnabled(ctx) && seriesItems.isNotEmpty())
            sendContentGroupNotification(apiName, "📺 Siri TV Baru", seriesItems)

        if (ContentDiscoveryManager.isNewAnimeEnabled(ctx) && animeItems.isNotEmpty())
            sendContentGroupNotification(apiName, "🇯🇵 Anime Baru", animeItems)

        if (ContentDiscoveryManager.isNewAsianEnabled(ctx) && asianItems.isNotEmpty())
            sendContentGroupNotification(apiName, "🇨🇳 Drama Asia / China Baru", asianItems)

        if (otherItems.isNotEmpty())
            sendContentGroupNotification(apiName, "🆕 Kandungan Baru", otherItems)
    }

    // ── Notification senders ──────────────────────────────────────────

    private fun sendContentGroupNotification(
        apiName: String,
        categoryTitle: String,
        items: List<SearchResponse>
    ) {
        val launchPi = makeLaunchIntent("$apiName$categoryTitle")
        val nameList = items.take(5).joinToString("\n• ", prefix = "• ") { it.name }
        val more = if (items.size > 5) "\n...dan ${items.size - 5} lagi" else ""
        val bigText = "$categoryTitle ditambah di $apiName:\n$nameList$more\n\nBuka MTSFlix untuk tonton sekarang!"

        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("$categoryTitle — $apiName")
            .setContentText("${items.size} tajuk baru: ${items.first().name}${if (items.size > 1) " dan lagi" else ""}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(launchPi)
            .build()

        nm.notify("$apiName$categoryTitle".hashCode(), notif)
    }

    private fun sendEpisodeNotification(
        showName: String,
        apiName: String,
        newEps: Int,
        totalEps: Int
    ) {
        val launchPi = makeLaunchIntent("$showName$apiName")
        val body = if (newEps == 1)
            "1 episod baru tersedia! (Jumlah: $totalEps episod)"
        else
            "$newEps episod baru tersedia! (Jumlah: $totalEps episod)"

        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("🔔 $showName")
            .setContentText("$body — $apiName")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("🔔 $showName\n\n$body\nProvider: $apiName\n\nTekan untuk menonton sekarang!")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(launchPi)
            .build()

        nm.notify("$showName$apiName".hashCode(), notif)
    }

    private fun makeLaunchIntent(key: String): PendingIntent {
        val intent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP) }
        return PendingIntent.getActivity(
            applicationContext, key.hashCode(), intent ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
