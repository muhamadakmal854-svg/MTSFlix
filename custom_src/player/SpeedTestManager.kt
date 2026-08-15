package com.mts.mtsflix.player

import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL

/**
 * MTSFlix Speed Test Manager v1.1.5
 * Menguji kelajuan setiap pautan pelayan video dan menyusun mengikut terpantas.
 */
object SpeedTestManager {

    private const val TEST_TIMEOUT_MS = 3000L

    /**
     * Uji masa respons HEAD request untuk satu URL.
     * Pulangkan masa dalam ms, atau Long.MAX_VALUE jika gagal/timeout.
     */
    suspend fun testLinkSpeed(url: String): Long {
        return withTimeoutOrNull(TEST_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                try {
                    val start = System.currentTimeMillis()
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.requestMethod = "HEAD"
                    conn.connectTimeout = TEST_TIMEOUT_MS.toInt()
                    conn.readTimeout = TEST_TIMEOUT_MS.toInt()
                    conn.instanceFollowRedirects = true
                    conn.connect()
                    val code = conn.responseCode
                    conn.disconnect()
                    if (code in 200..399) System.currentTimeMillis() - start
                    else Long.MAX_VALUE
                } catch (e: Exception) {
                    Long.MAX_VALUE
                }
            }
        } ?: Long.MAX_VALUE
    }

    /**
     * Susun senarai pautan mengikut kelajuan terpantas (parallel test).
     * Hanya menguji sehingga 8 pautan pertama untuk menjimatkan masa.
     */
    suspend fun sortLinksBySpeed(links: List<ExtractorLink>): List<ExtractorLink> {
        if (links.size <= 1) return links
        val testLinks = links.take(8)
        val rest = if (links.size > 8) links.drop(8) else emptyList()

        return coroutineScope {
            val speeds = testLinks.map { link ->
                async { Pair(link, testLinkSpeed(link.url)) }
            }.awaitAll()

            val sorted = speeds.sortedBy { it.second }.map { it.first }
            sorted + rest
        }
    }
}
