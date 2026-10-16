package com.bustime.app.data.sync

import android.content.Context
import android.util.Log
import com.bustime.app.data.local.TransportDao
import com.bustime.app.models.*
import com.bustime.app.utils.Constants
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Bundled first-run data plus revision-based Supabase deltas. */
class SnapshotSync(private val context: Context, private val dao: TransportDao) {
    private val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val localMutex = Mutex()
    private val syncMutex = Mutex()
    @Volatile var lastSyncFailed: Boolean = false
        private set

    suspend fun ensureLocalData() {
        withContext(Dispatchers.IO) {
            localMutex.withLock {
                try {
                    if (dao.countPlaces() == 0) {
                        prefs.edit().remove(KEY_VERSION).remove(KEY_ETAG).remove(KEY_HASH).remove(KEY_REVISION).apply()
                        importBundled()
                    }
                    Unit
                } catch (e: Exception) {
                    lastSyncFailed = true
                    Log.w(TAG, "Bundled data failed: ${e.message}")
                }
            }
        }
    }

    suspend fun sync(): Boolean {
        lastSyncFailed = false
        ensureLocalData()
        return withContext(Dispatchers.IO) {
            syncMutex.withLock {
                try {
                    val incremental = fetchIncremental()
                    if (incremental != null) return@withLock incremental
                    fetchRemoteSnapshot()
                } catch (e: Exception) {
                    lastSyncFailed = true
                    Log.w(TAG, "Sync failed; existing local data kept: ${e.message}")
                    false
                }
            }
        }
    }

    private suspend fun importBundled() {
        val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        applySnapshot(text, null)
    }

    /** null means unavailable and triggers snapshot fallback; false means already current. */
    private suspend fun fetchIncremental(): Boolean? {
        val state = getJson("${Constants.SUPABASE_URL}/functions/v1/bustime-data-sync?action=status")
            ?: return null
        if (!state.isJsonArray || state.asJsonArray.size() == 0) return null
        val stateRow = state.asJsonArray[0].asJsonObject
        val publishedRevision = stateRow.get("revision")?.asLong ?: 0L
        val localRevision = prefs.getLong(KEY_REVISION, 0L)
        if (publishedRevision == 0L) return null
        val publishedSnapshotVersion = stateRow.get("snapshot_version")?.asInt ?: 0
        val localSnapshotVersion = prefs.getInt(KEY_VERSION, 0)
        if (publishedRevision <= localRevision) {
            // Snapshot-only publishes can advance the baseline version without a
            // new delta revision. Detect that case instead of incorrectly reporting
            // "already current" and leaving this device on an older snapshot.
            if (publishedSnapshotVersion > localSnapshotVersion) {
                val refreshed = fetchRemoteSnapshot(forceFull = true)
                if (lastSyncFailed) return false
                prefs.edit()
                    .putLong(KEY_REVISION, publishedRevision)
                    .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                    .apply()
                return refreshed
            }
            return false
        }
        // A fresh install or an older build must replace its baseline first, so
        // records deleted before this device's first delta cursor cannot survive.
        var baselineChanged = false
        if (localRevision == 0L) {
            // Install the full baseline first, then replay the complete delta history.
            // MDM-published changes may not exist in routes.json, so skipping history
            // here would make MDM data disappear on fresh installs or older builds.
            baselineChanged = fetchRemoteSnapshot(forceFull = true)
            if (lastSyncFailed) return false
        }

        val changes = ArrayList<JsonObject>()
        var offset = 0
        while (true) {
            val page = getJson("${Constants.SUPABASE_URL}/functions/v1/bustime-data-sync?action=changes&since=$localRevision&offset=$offset")
                ?: return null
            if (!page.isJsonArray) return null
            val array = page.asJsonArray
            array.forEach { changes.add(it.asJsonObject) }
            if (array.size() < 1000) break
            offset += array.size()
            if (offset > 20000) return null
        }

        for (change in changes) {
            if (!applyChange(change)) {
                Log.w(TAG, "Invalid delta; revision cursor not advanced")
                return null
            }
        }
        // Only commit the cursor after every change has applied successfully.
        prefs.edit()
            .putLong(KEY_REVISION, publishedRevision)
            .putInt(KEY_VERSION, stateRow.get("snapshot_version")?.asInt ?: 0)
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .apply()
        Log.d(TAG, "Incremental revision $publishedRevision applied (${changes.size} changes)")
        return changes.isNotEmpty() || baselineChanged
    }

    private suspend fun applyChange(change: JsonObject): Boolean {
        val type = change.get("entity_type")?.asString ?: return false
        val id = change.get("entity_id")?.asString ?: return false
        val operation = change.get("operation")?.asString ?: return false
        val payload = change.get("payload")
        val revision = change.get("revision")?.asLong ?: 0L
        if (operation == "delete") {
            when (type) {
                "place" -> dao.deletePlace(id)
                "route" -> dao.deleteRouteAndStops(id)
                // A retired or unsupported entity must not block valid Bus sync events.
                else -> return true
            }
            return true
        }
        if (operation != "upsert" || payload == null || payload.isJsonNull) return false
        val existingPlaces = dao.getAllPlaces().map {
            PlaceDto(it.id, it.kind, it.names, null, it.city, it.district)
        }
        return try {
            when (type) {
                "place" -> {
                    // Revision 1 is the original bulk baseline. Keep the current
                    // bundled/remote snapshot authoritative for IDs it already has.
                    if (revision <= 1L && dao.getPlaces(listOf(id)).isNotEmpty()) {
                        Unit
                    } else {
                        val dto = gson.fromJson(payload, PlaceDto::class.java)
                        val data = SnapshotImporter.build(SnapshotDto(0, listOf(dto), emptyList()))
                        val item = data.places.firstOrNull { it.id == id } ?: return false
                        dao.upsertPlace(item)
                    }
                }
                "route" -> {
                    // Do not let the old bulk baseline overwrite a newer snapshot's
                    // route sequence or times. Later MDM revisions still replace it.
                    if (revision <= 1L && dao.getRoute(id) != null) {
                        Unit
                    } else {
                        val dto = gson.fromJson(payload, RouteDto::class.java)
                        val data = SnapshotImporter.build(SnapshotDto(0, existingPlaces, listOf(dto)))
                        val route = data.routes.firstOrNull { it.id == id } ?: return false
                        dao.upsertRouteWithStops(route, data.stops.filter { it.routeId == id })
                    }
                }
                // Ignore unsupported entity payloads without blocking the revision cursor.
                else -> Unit
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not apply $type/$id: ${e.message}")
            false
        }
    }

    private fun getJson(address: String): com.google.gson.JsonElement? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(address).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 20000
                setRequestProperty("apikey", Constants.SUPABASE_ANON_KEY)
                setRequestProperty("Authorization", "Bearer ${Constants.SUPABASE_ANON_KEY}")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
            }
            if (conn.responseCode !in 200..299) {
                Log.w(TAG, "Supabase sync HTTP ${conn.responseCode}")
                null
            } else conn.inputStream.bufferedReader().use { JsonParser().parse(it.readText()) }
        } catch (e: Exception) {
            Log.w(TAG, "Supabase sync request failed: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private suspend fun fetchRemoteSnapshot(forceFull: Boolean = false): Boolean {
        if (Constants.GITHUB_OWNER.startsWith("YOUR_")) {
            lastSyncFailed = true
            Log.w(TAG, "GitHub snapshot source is not configured")
            return false
        }
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(dataUrl()).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 20000
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Pragma", "no-cache")
                if (!forceFull) prefs.getString(KEY_ETAG, null)?.let { setRequestProperty("If-None-Match", it) }
            }
            return when (conn.responseCode) {
                200 -> applySnapshot(
                    conn.inputStream.bufferedReader().use { it.readText() },
                    conn.getHeaderField("ETag")
                )
                304 -> {
                    // A forced baseline fetch must return the actual snapshot.
                    lastSyncFailed = forceFull
                    if (forceFull) Log.w(TAG, "Server returned 304 during required full snapshot")
                    false
                }
                else -> { lastSyncFailed = true; Log.w(TAG, "Snapshot fetch HTTP ${conn.responseCode}"); false }
            }
        } finally {
            conn?.disconnect()
        }
    }

    private suspend fun applySnapshot(text: String, etag: String?): Boolean {
        val dto = gson.fromJson(text, SnapshotDto::class.java)
        if (dto == null) {
            lastSyncFailed = true
            Log.w(TAG, "Snapshot is empty or invalid")
            return false
        }
        val version = dto.dataVersion
        if (version == null) {
            lastSyncFailed = true
            Log.w(TAG, "Snapshot has no dataVersion")
            return false
        }
        val contentHash = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        val localVersion = prefs.getInt(KEY_VERSION, 0)
        val localHash = prefs.getString(KEY_HASH, null)
        if (version < localVersion) {
            lastSyncFailed = true
            Log.w(TAG, "Refusing older snapshot version $version (local $localVersion)")
            return false
        }
        if (version == localVersion && contentHash == localHash) {
            prefs.edit().putString(KEY_ETAG, etag ?: prefs.getString(KEY_ETAG, null)).apply()
            lastSyncFailed = false
            return false
        }
        val data = SnapshotImporter.build(dto)
        if (data.places.isEmpty()) {
            lastSyncFailed = true
            Log.w(TAG, "Refusing empty snapshot")
            return false
        }
        dao.replaceAll(data.places, data.routes, data.stops)
        prefs.edit().putInt(KEY_VERSION, version).putString(KEY_ETAG, etag)
            .putString(KEY_HASH, contentHash).putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
        lastSyncFailed = false
        return true
    }

    private fun dataUrl() =
        "https://raw.githubusercontent.com/${Constants.GITHUB_OWNER}/${Constants.GITHUB_REPO}/main/app/src/main/assets/$ASSET_NAME"

    companion object {
        private const val TAG = "SnapshotSync"
        private const val ASSET_NAME = "routes.json"
        private const val KEY_VERSION = "data_version"
        private const val KEY_ETAG = "data_etag"
        private const val KEY_LAST_SYNC = "data_last_sync"
        private const val KEY_HASH = "data_content_sha256"
        private const val KEY_REVISION = "data_sync_revision"
    }
}
