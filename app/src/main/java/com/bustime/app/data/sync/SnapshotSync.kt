package com.bustime.app.data.sync

import android.content.Context
import android.util.Log
import com.bustime.app.data.local.TransportDao
import com.bustime.app.models.SnapshotDto
import com.bustime.app.utils.Constants
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * पूरा डेटा एक फाइल (routes.json) में है।
 * 1. पहली बार: ऐप के अंदर रखी फाइल से तुरंत डेटा भर जाता है (बिना इंटरनेट भी)।
 * 2. फिर GitHub से फाइल देखता है: बदली न हो (304) तो कुछ डाउनलोड नहीं होता।
 * 3. डेटा तभी बदलता है जब नई फाइल सही हो और उसका dataVersion बड़ा हो। फेल हो तो पुराना डेटा बना रहता है।
 */
class SnapshotSync(private val context: Context, private val dao: TransportDao) {

    private val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val localMutex = Mutex()

    // स्क्रीन खुलने से पहले यह पक्का करता है कि डेटाबेस खाली न हो (पहली बार ऐप वाली फाइल से भरता है)
    suspend fun ensureLocalData() {
        withContext(Dispatchers.IO) {
            localMutex.withLock {
                try {
                    if (dao.countPlaces() == 0) {
                        // डेटाबेस खाली है: पुराने वर्ज़न/ETag भूल जाओ और ऐप वाली फाइल से भरो
                        prefs.edit().remove(KEY_VERSION).remove(KEY_ETAG).apply()
                        importBundled()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Bundled data failed: ${e.message}")
                }
            }
        }
    }

    suspend fun sync() {
        ensureLocalData()
        withContext(Dispatchers.IO) {
            try {
                fetchRemote()
            } catch (e: Exception) {
                Log.w(TAG, "Sync failed, old data kept: ${e.message}")
            }
        }
    }

    private suspend fun importBundled() {
        val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        applySnapshot(text, null)
    }

    private suspend fun fetchRemote() {
        if (Constants.GITHUB_OWNER.startsWith("YOUR_")) return
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(dataUrl()).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 20000
                prefs.getString(KEY_ETAG, null)?.let { setRequestProperty("If-None-Match", it) }
            }
            when (conn.responseCode) {
                200 -> {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    applySnapshot(text, conn.getHeaderField("ETag"))
                }
                304 -> Log.d(TAG, "Data unchanged")
                else -> Log.w(TAG, "Data fetch HTTP ${conn.responseCode}")
            }
        } finally {
            conn?.disconnect()
        }
    }

    private suspend fun applySnapshot(text: String, etag: String?): Boolean {
        val dto = gson.fromJson(text, SnapshotDto::class.java) ?: return false
        val version = dto.dataVersion ?: return false
        val localVersion = prefs.getInt(KEY_VERSION, 0)

        if (version <= localVersion) {
            if (etag != null) prefs.edit().putString(KEY_ETAG, etag).apply()
            return false
        }

        val data = SnapshotImporter.build(dto)
        // टूटी/खाली फाइल से पूरा डेटा मिट न जाए
        if (data.places.isEmpty() || (data.routes.isEmpty() && data.drivers.isEmpty())) return false

        dao.replaceAll(data.places, data.routes, data.stops, data.drivers, data.seaters)
        prefs.edit()
            .putInt(KEY_VERSION, version)
            .putString(KEY_ETAG, etag)
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .apply()
        Log.d(TAG, "Data updated to version $version")
        return true
    }

    // असली फाइल रिपो में यहीं है; ऐप में भी यही फाइल पहली बार के लिए रखी है
    private fun dataUrl() =
        "https://raw.githubusercontent.com/${Constants.GITHUB_OWNER}/${Constants.GITHUB_REPO}/main/app/src/main/assets/$ASSET_NAME"

    companion object {
        private const val TAG = "SnapshotSync"
        private const val ASSET_NAME = "routes.json"
        private const val KEY_VERSION = "data_version"
        private const val KEY_ETAG = "data_etag"
        private const val KEY_LAST_SYNC = "data_last_sync"
    }
}
