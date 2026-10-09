package com.bustime.app.data.repository

import android.content.Context
import com.bustime.app.data.local.AppDatabase
import com.bustime.app.data.sync.SnapshotSync
import com.bustime.app.models.BusResult
import com.bustime.app.models.Driver
import com.bustime.app.models.Place
import com.bustime.app.models.Route
import com.bustime.app.models.RouteStop
import com.bustime.app.models.StandResult
import com.bustime.app.utils.TextNormalizer

class AppRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val dao = AppDatabase.getDatabase(appContext).transportDao()
    private val snapshotSync = SnapshotSync(appContext, dao)

    // ऐप खुलने पर चलाओ: पहली बार डेटा भरता है, फिर बदलाव देखता है
    suspend fun syncData() = snapshotSync.sync()

    // हर सवाल से पहले पक्का करता है कि पहली बार का डेटा भर चुका हो
    private suspend fun <T> query(block: suspend () -> T): T {
        snapshotSync.ensureLocalData()
        return block()
    }

    // ---------- जगहें ----------
    // allowEmpty = true: टेक्स्ट खाली हो तो भी जगहों की सूची मिलती है (खोज पेज खुलते ही दिखाने के लिए)
    suspend fun searchPlaces(
        text: String,
        standsOnly: Boolean = false,
        limit: Int = 10,
        allowEmpty: Boolean = false
    ): List<Place> = query {
        val q = TextNormalizer.normalize(text)
        if (q.isEmpty() && !allowEmpty) emptyList()
        else dao.searchPlaces(q, if (standsOnly) "stand" else null, limit)
    }

    suspend fun getPlacesById(ids: Collection<String>): Map<String, Place> = query {
        if (ids.isEmpty()) emptyMap() else dao.getPlaces(ids.toList()).associateBy { it.id }
    }

    // ---------- बस ----------
    suspend fun searchBusRoutes(fromId: String, toId: String): List<BusResult> =
        query { dao.searchBusRoutes(fromId, toId) }

    suspend fun busesAtStand(standId: String): List<StandResult> = query { dao.busesAtStand(standId) }

    suspend fun getRoute(routeId: String): Route? = query { dao.getRoute(routeId) }

    suspend fun getRouteStops(routeId: String): List<RouteStop> = query { dao.getStops(routeId) }

    // ---------- टैक्सी / ऑटो ----------
    suspend fun getSeaterGroups(): List<Int> = query { dao.getSeaterGroups() }

    suspend fun getTaxis(seats: Int): List<Driver> = query { dao.getDrivers("taxi", seats, null) }

    // fuel: null = सारे, "ev" या "oil"
    suspend fun getAutos(fuel: String? = null): List<Driver> = query { dao.getDrivers("auto", null, fuel) }

    companion object {
        @Volatile
        private var INSTANCE: AppRepository? = null

        fun getInstance(context: Context): AppRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppRepository(context).also { INSTANCE = it }
            }
    }
}
