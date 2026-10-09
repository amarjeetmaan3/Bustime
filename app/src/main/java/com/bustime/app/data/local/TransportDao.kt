package com.bustime.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.bustime.app.models.BusResult
import com.bustime.app.models.Driver
import com.bustime.app.models.Place
import com.bustime.app.models.Route
import com.bustime.app.models.RouteStop
import com.bustime.app.models.SeaterGroup
import com.bustime.app.models.StandResult

@Dao
abstract class TransportDao {

    // ---------- सर्च ----------

    // जगह के सुझाव: किसी भी भाषा में टाइप करो; kind = "stand" देने पर सिर्फ़ बस स्टैंड
    @Query(
        "SELECT * FROM places WHERE (:kind IS NULL OR kind = :kind) " +
        "AND searchText LIKE '%' || :query || '%' " +
        "ORDER BY CASE WHEN searchText LIKE :query || '%' THEN 0 ELSE 1 END, searchText " +
        "LIMIT :limit"
    )
    abstract suspend fun searchPlaces(query: String, kind: String?, limit: Int): List<Place>

    @Query("SELECT * FROM places WHERE id IN (:ids)")
    abstract suspend fun getPlaces(ids: List<String>): List<Place>

    // From → To: वो रूट जिनमें From वाली जगह To वाली से पहले आती है, समय के क्रम में
    @Query(
        "SELECT r.id AS routeId, r.fromId AS routeFromId, r.toId AS routeToId, " +
        "r.serviceName AS serviceName, r.routeName AS routeName, r.contact AS contact, r.info AS info, " +
        "sa.time AS fromTime, sb.time AS toTime " +
        "FROM routes r " +
        "JOIN route_stops sa ON sa.routeId = r.id AND sa.placeId = :fromId " +
        "JOIN route_stops sb ON sb.routeId = r.id AND sb.placeId = :toId " +
        "WHERE sa.stopOrder < sb.stopOrder " +
        "ORDER BY COALESCE(sa.time, '99:99'), r.id"
    )
    abstract suspend fun searchBusRoutes(fromId: String, toId: String): List<BusResult>

    // बस स्टैंड सर्च: इस जगह से गुज़रने वाली सारी बसें समय के क्रम में
    @Query(
        "SELECT r.id AS routeId, r.fromId AS routeFromId, r.toId AS routeToId, " +
        "r.serviceName AS serviceName, r.routeName AS routeName, r.contact AS contact, r.info AS info, " +
        "s.time AS atTime " +
        "FROM routes r JOIN route_stops s ON s.routeId = r.id " +
        "WHERE s.placeId = :standId " +
        "ORDER BY COALESCE(s.time, '99:99'), r.id"
    )
    abstract suspend fun busesAtStand(standId: String): List<StandResult>

    @Query("SELECT * FROM routes WHERE id = :routeId")
    abstract suspend fun getRoute(routeId: String): Route?

    @Query("SELECT * FROM routes ORDER BY fromId, toId, time")
    abstract suspend fun getAllRoutes(): List<Route>

    @Query("SELECT * FROM route_stops WHERE routeId = :routeId ORDER BY stopOrder")
    abstract suspend fun getStops(routeId: String): List<RouteStop>

    // ---------- टैक्सी / ऑटो ----------

    @Query("SELECT seats FROM seater_groups ORDER BY seats")
    abstract suspend fun getSeaterGroups(): List<Int>

    @Query(
        "SELECT * FROM drivers WHERE type = :type " +
        "AND (:seats IS NULL OR seats = :seats) " +
        "AND (:fuel IS NULL OR fuel = :fuel) " +
        "ORDER BY name"
    )
    abstract suspend fun getDrivers(type: String, seats: Int?, fuel: String?): List<Driver>

    // ---------- सिंक ----------

    @Query("SELECT COUNT(*) FROM places")
    abstract suspend fun countPlaces(): Int

    @Query("DELETE FROM places") abstract suspend fun clearPlaces()
    @Query("DELETE FROM routes") abstract suspend fun clearRoutes()
    @Query("DELETE FROM route_stops") abstract suspend fun clearStops()
    @Query("DELETE FROM drivers") abstract suspend fun clearDrivers()
    @Query("DELETE FROM seater_groups") abstract suspend fun clearSeaters()

    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertPlaces(items: List<Place>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertRoutes(items: List<Route>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertStops(items: List<RouteStop>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertDrivers(items: List<Driver>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertSeaters(items: List<SeaterGroup>)

    // पूरा डेटा एक ही लेन-देन में बदलता है: या सब नया, या सब पुराना (आधा-अधूरा कभी नहीं)
    @Transaction
    open suspend fun replaceAll(
        places: List<Place>,
        routes: List<Route>,
        stops: List<RouteStop>,
        drivers: List<Driver>,
        seaters: List<SeaterGroup>
    ) {
        clearPlaces(); clearRoutes(); clearStops(); clearDrivers(); clearSeaters()
        insertPlaces(places); insertRoutes(routes); insertStops(stops)
        insertDrivers(drivers); insertSeaters(seaters)
    }
}
