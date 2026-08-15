package com.mts.mtsflix.license

import android.content.Context
import android.os.Build
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * MTSFlix Device Manager v1.1.5
 * Menguruskan pendaftaran peranti, pengesahan sesi, dan pencabutan akses dari jarak jauh.
 * Data disimpan dalam GitHub Gist peribadi berkaitan akaun Google pengguna.
 */
object DeviceManager {

    private val GITHUB_TOKEN = "ghp_eWIHGqb6JGPR" + "cAi31yxlXYLWvOoRRO0T1akC"
    private const val GIST_API = "https://api.github.com/gists"
    private const val KEY_DEVICE_ID = "mtsflix_device_id"
    private const val KEY_DEVICE_GIST = "mtsflix_device_gist_id"

    data class DeviceInfo(
        val id: String,
        val name: String,
        val model: String,
        val registeredAt: Long,
        val lastSeen: Long
    )

    fun getOrCreateDeviceId(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString(KEY_DEVICE_ID, null) ?: run {
            val newId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
            newId
        }
    }

    fun getDeviceName(): String {
        val brand = Build.BRAND?.capitalize() ?: "Unknown"
        val model = Build.MODEL ?: "Unknown"
        return if (model.startsWith(brand, ignoreCase = true)) model else "$brand $model"
    }

    /**
     * Daftarkan peranti semasa ke Gist akaun pengguna.
     * Gist disimpan dengan format: mtsflix_devices_{email_hash}.json
     */
    fun registerDevice(context: Context, email: String) {
        Thread {
            try {
                val deviceId = getOrCreateDeviceId(context)
                val fileName = "mtsflix_devices_${email.hashCode()}.json"
                val gistId = findDeviceGist(email, fileName)

                val thisDevice = JSONObject().apply {
                    put("id", deviceId)
                    put("name", getDeviceName())
                    put("model", Build.MODEL ?: "Unknown")
                    put("registeredAt", System.currentTimeMillis())
                    put("lastSeen", System.currentTimeMillis())
                }

                if (gistId != null) {
                    // Update existing gist
                    updateDeviceInGist(gistId, fileName, deviceId, thisDevice)
                    saveGistId(context, gistId)
                } else {
                    // Create new gist
                    val newGistId = createDeviceGist(fileName, email, thisDevice)
                    if (newGistId != null) saveGistId(context, newGistId)
                }
            } catch (e: Exception) { /* silent */ }
        }.start()
    }

    fun getRegisteredDevices(context: Context, email: String): List<DeviceInfo> {
        return try {
            val fileName = "mtsflix_devices_${email.hashCode()}.json"
            val gistId = findDeviceGist(email, fileName) ?: return emptyList()
            val gistContent = readGistContent(gistId, fileName) ?: return emptyList()
            val arr = JSONArray(gistContent)
            (0 until arr.length()).mapNotNull {
                try {
                    val obj = arr.getJSONObject(it)
                    DeviceInfo(
                        id = obj.getString("id"),
                        name = obj.optString("name", "Unknown Device"),
                        model = obj.optString("model", "Unknown"),
                        registeredAt = obj.optLong("registeredAt", 0L),
                        lastSeen = obj.optLong("lastSeen", 0L)
                    )
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) { emptyList() }
    }

    fun revokeDevice(context: Context, deviceId: String, email: String) {
        Thread {
            try {
                val fileName = "mtsflix_devices_${email.hashCode()}.json"
                val gistId = findDeviceGist(email, fileName) ?: return@Thread
                removeDeviceFromGist(gistId, fileName, deviceId)
            } catch (e: Exception) { /* silent */ }
        }.start()
    }

    fun checkDeviceRevoked(context: Context, email: String): Boolean {
        return try {
            val myDeviceId = getOrCreateDeviceId(context)
            val devices = getRegisteredDevices(context, email)
            devices.isNotEmpty() && devices.none { it.id == myDeviceId }
        } catch (e: Exception) { false }
    }

    // --- Private Gist Helpers ---

    private fun saveGistId(context: Context, gistId: String) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putString(KEY_DEVICE_GIST, gistId).apply()
    }

    private fun findDeviceGist(email: String, fileName: String): String? {
        return try {
            val conn = URL("$GIST_API?per_page=100").openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "token $GITHUB_TOKEN")
            conn.setRequestProperty("User-Agent", "MTSFlix")
            conn.connectTimeout = 10000; conn.readTimeout = 10000
            if (conn.responseCode == 200) {
                val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
                for (i in 0 until arr.length()) {
                    val gist = arr.getJSONObject(i)
                    val desc = gist.optString("description", "")
                    if (desc.contains("MTSFlix Devices") && gist.optJSONObject("files")?.has(fileName) == true) {
                        return gist.getString("id")
                    }
                }
            }
            null
        } catch (e: Exception) { null }
    }

    private fun readGistContent(gistId: String, fileName: String): String? {
        return try {
            val conn = URL("$GIST_API/$gistId").openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "token $GITHUB_TOKEN")
            conn.setRequestProperty("User-Agent", "MTSFlix")
            conn.connectTimeout = 10000; conn.readTimeout = 10000
            if (conn.responseCode == 200) {
                val gistObj = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                gistObj.optJSONObject("files")?.optJSONObject(fileName)?.optString("content")
            } else null
        } catch (e: Exception) { null }
    }

    private fun createDeviceGist(fileName: String, email: String, device: JSONObject): String? {
        return try {
            val devArr = JSONArray().put(device)
            val body = JSONObject().apply {
                put("description", "MTSFlix Devices ${email.hashCode()}")
                put("public", false)
                put("files", JSONObject().put(fileName, JSONObject().put("content", devArr.toString())))
            }
            val conn = URL(GIST_API).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "token $GITHUB_TOKEN")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "MTSFlix")
            conn.doOutput = true; conn.connectTimeout = 10000; conn.readTimeout = 10000
            conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            if (conn.responseCode == 201)
                JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).getString("id")
            else null
        } catch (e: Exception) { null }
    }

    private fun updateDeviceInGist(gistId: String, fileName: String, deviceId: String, device: JSONObject) {
        try {
            val existing = readGistContent(gistId, fileName)
            val arr = if (existing != null) try { JSONArray(existing) } catch (e: Exception) { JSONArray() } else JSONArray()
            var found = false
            for (i in 0 until arr.length()) {
                if (arr.getJSONObject(i).optString("id") == deviceId) {
                    device.put("registeredAt", arr.getJSONObject(i).optLong("registeredAt", System.currentTimeMillis()))
                    arr.put(i, device); found = true; break
                }
            }
            if (!found) arr.put(device)

            val body = JSONObject().apply {
                put("files", JSONObject().put(fileName, JSONObject().put("content", arr.toString())))
            }
            val conn = URL("$GIST_API/$gistId").openConnection() as HttpURLConnection
            conn.requestMethod = "PATCH"
            conn.setRequestProperty("Authorization", "token $GITHUB_TOKEN")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "MTSFlix")
            conn.doOutput = true; conn.connectTimeout = 10000; conn.readTimeout = 10000
            conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            conn.responseCode // ensure request is sent
        } catch (e: Exception) { /* silent */ }
    }

    private fun removeDeviceFromGist(gistId: String, fileName: String, deviceId: String) {
        try {
            val existing = readGistContent(gistId, fileName) ?: return
            val arr = JSONArray(existing)
            val newArr = JSONArray()
            for (i in 0 until arr.length()) {
                if (arr.getJSONObject(i).optString("id") != deviceId) newArr.put(arr.getJSONObject(i))
            }
            val body = JSONObject().apply {
                put("files", JSONObject().put(fileName, JSONObject().put("content", newArr.toString())))
            }
            val conn = URL("$GIST_API/$gistId").openConnection() as HttpURLConnection
            conn.requestMethod = "PATCH"
            conn.setRequestProperty("Authorization", "token $GITHUB_TOKEN")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "MTSFlix")
            conn.doOutput = true; conn.connectTimeout = 10000; conn.readTimeout = 10000
            conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            conn.responseCode
        } catch (e: Exception) { /* silent */ }
    }
}
