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
import java.util.UUID

/** Bundled first-run data plus revision-based, recoverable Supabase deltas. */
class SnapshotSync(private val context: Context, private val dao: TransportDao) {
    private val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val localMutex = Mutex()
    private val syncMutex = Mutex()

    @Volatile var lastSyncFailed: Boolean = false
        private set

    private data class FailedChange(
        val revision: Long,
        val entityType: String,
        val entityId: String,
        val message: String,
        val errorCode: String = "change_apply_failed"
    ) {
        fun toJson(): Map<String, Any> = mapOf(
            "revision" to revision,
            "entity_type" to entityType,
            "entity_id" to entityId,
            "error_code" to errorCode,
            "error_message" to message.take(300)
        )
        fun keyJson(): Map<String, Any> = mapOf(
            "revision" to revision,
            "entity_type" to entityType,
            "entity_id" to entityId
        )
    }

    suspend fun ensureLocalData() {
        withContext(Dispatchers.IO) {
            localMutex.withLock {
                try {
                    if (dao.countPlaces() == 0) {
                        prefs.edit().remove(KEY_VERSION).remove(KEY_ETAG).remove(KEY_HASH).remove(KEY_REVISION).apply()
                        if (!importRemoteSnapshot()) importBundled()
                    }
                    Unit
                } catch (e: Exception) {
                    lastSyncFailed = true
                    Log.w(TAG, "Initial data load failed: ${e.message}")
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
                    val result = fetchIncremental()
                    if (result == null) {
                        lastSyncFailed = true
                        false
                    } else result
                } catch (e: Exception) {
                    lastSyncFailed = true
                    Log.w(TAG, "Sync failed; existing local data kept where possible: ${e.message}")
                    false
                }
            }
        }
    }

    suspend fun getSyncStatus(): String = withContext(Dispatchers.IO) {
        val state = getJson("${Constants.SUPABASE_URL}/functions/v1/bustime-data-sync?action=status")
        val localRevision = prefs.getLong(KEY_REVISION, 0L)
        val localVersion = prefs.getInt(KEY_VERSION, 0)
        if (state == null || !state.isJsonArray || state.asJsonArray.size() == 0) {
            return@withContext "Local revision $localRevision · snapshot v$localVersion · server status unavailable"
        }
        val row = state.asJsonArray[0].asJsonObject
        val serverRevision = row.get("revision")?.asLong ?: 0L
        val serverVersion = row.get("snapshot_version")?.asInt ?: 0
        "Local revision $localRevision / Server revision $serverRevision · Local snapshot v$localVersion / Server snapshot v$serverVersion"
    }

    private suspend fun importRemoteSnapshot(): Boolean {
        val response = getJson("${Constants.SUPABASE_URL}/functions/v1/bustime-data-sync?action=snapshot")
            ?: return false
        if (!response.isJsonObject || response.asJsonObject.has("error")) return false
        return try {
            val dto = gson.fromJson(response, SnapshotDto::class.java) ?: return false
            if (dto.dataVersion == null || dto.snapshotRevision == null ||
                dto.places.isNullOrEmpty() || dto.routes.isNullOrEmpty()) return false
            applySnapshot(gson.toJson(dto), null, requireCleanData = true)
        } catch (e: Exception) {
            Log.w(TAG, "Remote master snapshot invalid; local data retained: ${e.message}")
            false
        }
    }

    private suspend fun importBundled() {
        val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        applySnapshot(text, null, requireCleanData = false)
    }

    /**
     * Replays changes in bounded pages. A malformed individual row is recorded and skipped;
     * the revision cursor advances only after the server durably records those failures.
     */
    private suspend fun fetchIncremental(): Boolean? {
        val state = getJson("${Constants.SUPABASE_URL}/functions/v1/bustime-data-sync?action=status")
            ?: return null
        if (!state.isJsonArray || state.asJsonArray.size() == 0) return null
        val stateRow = state.asJsonArray[0].asJsonObject
        val publishedRevision = stateRow.get("revision")?.asLong ?: 0L
        val localRevision = prefs.getLong(KEY_REVISION, 0L)
        if (publishedRevision <= 0L) return null

        val failures = ArrayList<FailedChange>()
        val resolved = ArrayList<FailedChange>()
        var changed = false
        var snapshotRetried = false
        val deviceId = getDeviceId()

        // Retry failed records after their backoff. The server excludes superseded events,
        // preventing an old retry from overwriting a newer route version.
        val retryRows = getJson(
            "${Constants.SUPABASE_URL}/functions/v1/bustime-data-sync?action=retry&device_id=$deviceId"
        )
        if (retryRows != null && retryRows.isJsonArray) {
            for (element in retryRows.asJsonArray) {
                if (!element.isJsonObject) continue
                val change = element.asJsonObject
                val error = if (change.get("operation")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString == "snapshot_retry") {
                    if (importRemoteSnapshot()) { snapshotRetried = true; null } else "Snapshot retry did not produce a newer valid snapshot"
                } else {
                    applyChange(change)
                }
                val item = failedChange(change, error)
                if (error != null) {
                    if (item == null) return null
                    failures.add(item)
                } else {
                    val success = failedChange(change, null)
                    if (success != null) resolved.add(success)
                    changed = true
                }
            }
        } else if (retryRows != null && retryRows.isJsonObject && retryRows.asJsonObject.has("error")) {
            Log.w(TAG, "Retry queue unavailable: ${retryRows.asJsonObject.get("error")}")
        }

        if (snapshotRetried) {
            val snapshotRevision = prefs.getLong(KEY_REVISION, 0L)
            val reported = reportSync(
                snapshotRevision,
                failures + pendingSnapshotFailures(),
                resolved,
                snapshotRecovered = true
            )
            if (!reported) return null
            clearPendingSnapshotFailures()
            Log.d(TAG, "Recovered a previously invalid snapshot record at revision $snapshotRevision")
            return true
        }

        if (publishedRevision > localRevision) {
            var offset = 0
            var processed = 0
            while (true) {
                val page = getJson(
                    "${Constants.SUPABASE_URL}/functions/v1/bustime-data-sync?action=changes&since=$localRevision&until=$publishedRevision&offset=$offset"
                ) ?: return null

                if (page.isJsonObject && page.asJsonObject.get("error")?.asString == "snapshot_required") {
                    if (!importRemoteSnapshot()) return null
                    val snapshotRevision = prefs.getLong(KEY_REVISION, 0L)
                    val snapshotFailures = pendingSnapshotFailures()
                    val reported = reportSync(snapshotRevision, snapshotFailures, emptyList(), snapshotRecovered = true)
                    if (reported) clearPendingSnapshotFailures()
                    if (!reported) return null
                    Log.d(TAG, "Recovered with full snapshot at revision $snapshotRevision")
                    return true
                }
                if (!page.isJsonArray) return null

                val rows = page.asJsonArray
                for (element in rows) {
                    // A row without a parseable identity cannot be safely logged or skipped.
                    if (!element.isJsonObject) return null
                    val change = element.asJsonObject
                    val error = applyChange(change)
                    val item = failedChange(change, error)
                    if (error != null) {
                        if (item == null) return null
                        failures.add(item)
                        Log.w(TAG, "Skipped invalid change ${item.entityType}/${item.entityId} at revision ${item.revision}: ${item.message}")
                    } else {
                        changed = true
                    }
                    processed++
                }
                if (rows.size() < PAGE_SIZE) break
                offset += rows.size()
                // At the 20,000-row safety boundary, the next page request returns
                // snapshot_required and the full master snapshot replaces the old baseline.
                if (offset >= MAX_INCREMENTAL_ROWS) continue
            }

            if (!reportSync(publishedRevision, failures + pendingSnapshotFailures(), resolved, snapshotRecovered = false)) return null
            clearPendingSnapshotFailures()
            prefs.edit()
                .putLong(KEY_REVISION, publishedRevision)
                .putInt(KEY_VERSION, stateRow.get("snapshot_version")?.asInt ?: 0)
                .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                .apply()
            Log.d(TAG, "Incremental revision $publishedRevision processed ($processed rows; ${failures.size} failed)")
            return changed || resolved.isNotEmpty()
        }

        val snapshotReportPending = prefs.getBoolean(KEY_SNAPSHOT_REPORT_PENDING, false)
        val reported = reportSync(localRevision, failures + pendingSnapshotFailures(), resolved, snapshotRecovered = snapshotReportPending)
        if (!reported) return null
        clearPendingSnapshotFailures()
        prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
        return resolved.isNotEmpty()
    }

    private fun pendingSnapshotFailures(): List<FailedChange> {
        val raw = prefs.getString(KEY_PENDING_SNAPSHOT_FAILURES, null) ?: return emptyList()
        return try {
            val json = JsonParser().parse(raw)
            if (!json.isJsonArray) return emptyList()
            json.asJsonArray.mapNotNull { element ->
                if (!element.isJsonObject) null else failedChange(element.asJsonObject, element.asJsonObject.get("error_message")?.asString)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun clearPendingSnapshotFailures() {
        prefs.edit().remove(KEY_PENDING_SNAPSHOT_FAILURES).remove(KEY_SNAPSHOT_REPORT_PENDING).apply()
    }

    private fun failedChange(change: JsonObject, message: String?): FailedChange? {
        return try {
            val revision = (change.get("failure_revision") ?: change.get("revision"))?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong ?: return null
            val type = change.get("entity_type")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: return null
            val id = change.get("entity_id")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.trim() ?: return null
            val code = change.get("error_code")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                ?: "change_apply_failed"
            if (revision < 1L || type !in listOf("place", "route") || id.isEmpty()) null
            else FailedChange(revision, type, id, message ?: "", code)
        } catch (_: Exception) {
            null
        }
    }

    private fun getDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, created).commit()
        return created
    }

    private fun appVersion(): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun reportSync(
        revision: Long,
        failures: List<FailedChange>,
        resolved: List<FailedChange>,
        snapshotRecovered: Boolean
    ): Boolean {
        val batchSize = 100
        val batchCount = maxOf(
            1,
            (failures.size + batchSize - 1) / batchSize,
            (resolved.size + batchSize - 1) / batchSize
        )
        for (batch in 0 until batchCount) {
            val from = batch * batchSize
            val failureBatch = failures.drop(from).take(batchSize)
            val resolvedBatch = resolved.drop(from).take(batchSize)
            val isFinalBatch = batch == batchCount - 1
            val body = mapOf(
                "action" to "report",
                "device_id" to getDeviceId(),
                "app_version" to appVersion(),
                "revision" to revision,
                "failures" to failureBatch.map { it.toJson() },
                "resolved" to resolvedBatch.map { it.keyJson() },
                "snapshot_recovered" to (snapshotRecovered && batch == 0),
                "advance_checkpoint" to isFinalBatch
            )
            val response = postJson(
                "${Constants.SUPABASE_URL}/functions/v1/bustime-data-sync",
                gson.toJson(body)
            )
            val ok = response != null && response.isJsonObject &&
                response.asJsonObject.get("ok")?.asBoolean == true
            if (!ok) {
                Log.w(TAG, "Could not save sync report batch ${batch + 1}/$batchCount; local cursor will not advance")
                return false
            }
        }
        return true
    }

    /** null means success; a message means this one change failed and may be retried. */
    private suspend fun applyChange(change: JsonObject): String? {
        return try {
            val type = change.get("entity_type")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                ?: return "Missing or invalid entity type"
            val id = change.get("entity_id")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.trim()
                ?.takeIf { it.isNotEmpty() } ?: return "Missing or invalid entity ID"
            val operation = change.get("operation")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                ?: return "Missing or invalid operation"
            val payload = change.get("payload")
            val revision = change.get("revision")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong
                ?: return "Missing or invalid revision"

            if (type !in listOf("place", "route")) return null
            if (operation == "delete") {
                when (type) {
                    "place" -> dao.deletePlace(id)
                    "route" -> dao.deleteRouteAndStops(id)
                }
                return null
            }
            if (operation != "upsert" || payload == null || payload.isJsonNull) return "Invalid upsert payload"

            val existingPlaces = dao.getAllPlaces().map {
                PlaceDto(it.id, it.kind, it.names, null, it.city, it.district)
            }
            when (type) {
                "place" -> {
                    if (revision <= 1L && dao.getPlaces(listOf(id)).isNotEmpty()) {
                        Unit
                    } else {
                        val dto = gson.fromJson(payload, PlaceDto::class.java)
                        val data = SnapshotImporter.build(SnapshotDto(0, listOf(dto), emptyList()))
                        if (data.skipped > 0) return "Place validation failed"
                        val item = data.places.firstOrNull { it.id == id } ?: return "Place payload did not produce a valid place"
                        dao.upsertPlace(item)
                    }
                }
                "route" -> {
                    if (revision <= 1L && dao.getRoute(id) != null) {
                        Unit
                    } else {
                        val dto = gson.fromJson(payload, RouteDto::class.java)
                        if (dto.hidden == true) {
                            dao.deleteRouteAndStops(id)
                        } else {
                            val data = SnapshotImporter.build(SnapshotDto(0, existingPlaces, listOf(dto)))
                            if (data.skipped > 0) return "Route or stop validation failed"
                            val route = data.routes.firstOrNull { it.id == id }
                                ?: return "Route payload did not produce a valid route"
                            dao.upsertRouteWithStops(route, data.stops.filter { it.routeId == id })
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            e.message ?: "Change could not be applied"
        }
    }

    private fun getJson(address: String): com.google.gson.JsonElement? = requestJson(address, null)

    private fun postJson(address: String, body: String): com.google.gson.JsonElement? =
        requestJson(address, body)

    private fun requestJson(address: String, body: String?): com.google.gson.JsonElement? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(address).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 25000
                requestMethod = if (body == null) "GET" else "POST"
                setRequestProperty("apikey", Constants.SUPABASE_ANON_KEY)
                setRequestProperty("Authorization", "Bearer ${Constants.SUPABASE_ANON_KEY}")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            if (body != null) conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: return null
            if (status !in 200..299) Log.w(TAG, "Supabase sync HTTP $status: ${text.take(160)}")
            JsonParser().parse(text)
        } catch (e: Exception) {
            Log.w(TAG, "Supabase sync request failed: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private suspend fun applySnapshot(
        text: String,
        etag: String?,
        requireCleanData: Boolean
    ): Boolean {
        val dto = try { gson.fromJson(text, SnapshotDto::class.java) } catch (e: Exception) {
            lastSyncFailed = true
            Log.w(TAG, "Snapshot JSON invalid: ${e.message}")
            return false
        }
        if (dto == null || dto.dataVersion == null || dto.snapshotRevision == null) {
            lastSyncFailed = true
            Log.w(TAG, "Snapshot is missing required version information")
            return false
        }
        val contentHash = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        val localVersion = prefs.getInt(KEY_VERSION, 0)
        val localHash = prefs.getString(KEY_HASH, null)
        if (dto.dataVersion < localVersion) {
            lastSyncFailed = true
            Log.w(TAG, "Refusing older snapshot version ${dto.dataVersion} (local $localVersion)")
            return false
        }
        if (dto.dataVersion == localVersion && contentHash == localHash &&
            dto.snapshotRevision == prefs.getLong(KEY_REVISION, 0L)) {
            prefs.edit().putString(KEY_ETAG, etag ?: prefs.getString(KEY_ETAG, null)).apply()
            lastSyncFailed = false
            return false
        }

        val data = SnapshotImporter.build(dto)
        if (data.places.isEmpty() || data.routes.isEmpty()) {
            lastSyncFailed = true
            Log.w(TAG, "Snapshot has no usable places or routes; existing local data retained")
            return false
        }

        // Preserve the previous local copy of any identifiable invalid snapshot record.
        // Also preserve old places required by a retained route so no route becomes dangling.
        val oldPlaces = dao.getAllPlaces().associateBy { it.id }
        val oldRoutes = dao.getAllRoutes().associateBy { it.id }
        val oldStops = dao.getAllStops().groupBy { it.routeId }
        val mergedPlaces = LinkedHashMap<String, Place>()
        data.places.forEach { mergedPlaces[it.id] = it }
        for (id in data.failedPlaceIds) oldPlaces[id]?.let { mergedPlaces[id] = it }

        val mergedRoutes = LinkedHashMap<String, Route>()
        data.routes.forEach { mergedRoutes[it.id] = it }
        val mergedStops = data.stops.toMutableList()
        for (id in data.failedRouteIds) {
            val oldRoute = oldRoutes[id] ?: continue
            mergedRoutes[id] = oldRoute
            val retainedStops = oldStops[id].orEmpty()
            mergedStops.removeAll { it.routeId == id }
            mergedStops.addAll(retainedStops)
            retainedStops.forEach { stop -> oldPlaces[stop.placeId]?.let { mergedPlaces.putIfAbsent(it.id, it) } }
        }

        val safeRoutes = mergedRoutes.values.filter { route ->
            val routeStops = mergedStops.filter { it.routeId == route.id }.sortedBy { it.stopOrder }
            val valid = route.fromId in mergedPlaces && route.toId in mergedPlaces &&
                routeStops.isNotEmpty() && routeStops.all { it.placeId in mergedPlaces } &&
                routeStops.first().placeId == route.fromId && routeStops.last().placeId == route.toId
            if (!valid) Log.w(TAG, "Snapshot route ${route.id} retained only if its complete stop chain is valid")
            valid
        }
        val safeRouteIds = safeRoutes.map { it.id }.toSet()
        val safeStops = mergedStops.filter { it.routeId in safeRouteIds }
        if (mergedPlaces.isEmpty() || safeRoutes.isEmpty()) {
            lastSyncFailed = true
            Log.w(TAG, "Snapshot recovery produced no complete dataset; existing local data retained")
            return false
        }

        val snapshotFailures = ArrayList<FailedChange>()
        if (requireCleanData && dto.snapshotRevision > 0L) {
            data.failedPlaceIds.forEach { snapshotFailures.add(FailedChange(dto.snapshotRevision, "place", it, "Snapshot record failed validation", "snapshot_record_invalid")) }
            data.failedRouteIds.forEach { snapshotFailures.add(FailedChange(dto.snapshotRevision, "route", it, "Snapshot record or stop chain failed validation", "snapshot_route_invalid")) }
        }

        // The replacement remains one Room transaction; valid data is imported and old copies
        // of identifiable bad records are retained instead of rejecting the entire snapshot.
        dao.replaceAll(mergedPlaces.values.toList(), safeRoutes, safeStops)
        prefs.edit()
            .putInt(KEY_VERSION, dto.dataVersion)
            .putString(KEY_ETAG, etag)
            .putString(KEY_HASH, contentHash)
            .putLong(KEY_REVISION, dto.snapshotRevision)
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .putString(KEY_PENDING_SNAPSHOT_FAILURES, gson.toJson(snapshotFailures.map { it.toJson() }))
            .putBoolean(KEY_SNAPSHOT_REPORT_PENDING, snapshotFailures.isNotEmpty())
            .apply()
        if (data.skipped > 0) Log.w(TAG, "Snapshot imported with ${data.skipped} isolated invalid records")

        lastSyncFailed = false
        return true
    }

    companion object {
        private const val TAG = "SnapshotSync"
        private const val ASSET_NAME = "routes.json"
        private const val PAGE_SIZE = 1000
        private const val MAX_INCREMENTAL_ROWS = 20000
        private const val KEY_VERSION = "data_version"
        private const val KEY_ETAG = "data_etag"
        private const val KEY_LAST_SYNC = "data_last_sync"
        private const val KEY_HASH = "data_content_sha256"
        private const val KEY_REVISION = "data_sync_revision"
        private const val KEY_DEVICE_ID = "data_sync_device_id"
        private const val KEY_SNAPSHOT_REPORT_PENDING = "data_sync_snapshot_report_pending"
        private const val KEY_PENDING_SNAPSHOT_FAILURES = "data_sync_pending_snapshot_failures"
    }
}
