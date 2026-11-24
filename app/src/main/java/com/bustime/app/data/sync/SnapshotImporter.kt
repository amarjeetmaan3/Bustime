package com.bustime.app.data.sync

import android.util.Log
import com.bustime.app.models.Place
import com.bustime.app.models.Route
import com.bustime.app.models.RouteStop
import com.bustime.app.models.SnapshotDto
import com.bustime.app.utils.TextNormalizer

/** routes.json को जाँचकर वैध रिकॉर्ड बनाता है और खराब रिकॉर्ड की पहचान अलग रखता है। */
object SnapshotImporter {

    class Result(
        val places: List<Place>,
        val routes: List<Route>,
        val stops: List<RouteStop>,
        val skipped: Int,
        val failedPlaceIds: Set<String>,
        val failedRouteIds: Set<String>
    )

    private const val TAG = "SnapshotImporter"
    private val TIME = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

    private fun clean(s: String?): String? = s?.trim()?.takeIf { it.isNotEmpty() }

    fun build(dto: SnapshotDto): Result {
        var skipped = 0
        val failedPlaces = LinkedHashSet<String>()
        val places = LinkedHashMap<String, Place>()

        for (p in dto.places.orEmpty()) {
            val id = clean(p.id)
            val names = LinkedHashMap<String, String>()
            for ((k, v) in p.name.orEmpty()) {
                if (!k.isNullOrBlank() && !v.isNullOrBlank()) {
                    names[k.trim().lowercase()] = v.trim()
                }
            }
            if (id == null || names.isEmpty() || places.containsKey(id)) {
                skipped++
                if (id != null) {
                    failedPlaces.add(id)
                    places.remove(id)
                }
                continue
            }
            val search = (names.values + p.aliases.orEmpty().filterNotNull())
                .map { TextNormalizer.normalize(it) }
                .filter { it.isNotEmpty() }
                .joinToString("|")
            places[id] = Place(
                id = id,
                kind = clean(p.kind)?.lowercase() ?: "place",
                names = names,
                city = clean(p.city),
                district = clean(p.district),
                searchText = search
            )
        }

        val routes = ArrayList<Route>()
        val stops = ArrayList<RouteStop>()
        val failedRoutes = LinkedHashSet<String>()
        val routeIds = HashSet<String>()

        for (r in dto.routes.orEmpty()) {
            if (r.hidden == true) continue
            val id = clean(r.id)
            if (id == null) {
                skipped++
                continue
            }
            if (!routeIds.add(id)) {
                skipped++
                failedRoutes.add(id)
                routes.removeAll { it.id == id }
                stops.removeAll { it.routeId == id }
                continue
            }

            val from = clean(r.from)
            val to = clean(r.to)
            val startTime = clean(r.time)?.takeIf { TIME.matches(it) }
            val arriveTime = clean(r.arrive)?.takeIf { TIME.matches(it) }
            val intermediate = r.stops.orEmpty()
            val intermediateIds = ArrayList<String>()
            var routeInvalid = from == null || to == null || from == to ||
                from !in places || to !in places

            for (s in intermediate) {
                val pid = clean(s.place)
                if (pid == null || pid !in places || pid == from || pid == to || pid in intermediateIds) {
                    routeInvalid = true
                } else {
                    intermediateIds.add(pid)
                }
            }

            if (routeInvalid) {
                skipped++
                failedRoutes.add(id)
                continue
            }

            val time = startTime ?: intermediate.firstNotNullOfOrNull { clean(it.time)?.takeIf(TIME::matches) } ?: arriveTime
            var order = 0
            stops.add(RouteStop(id, order++, from!!, startTime))
            for (s in intermediate) {
                stops.add(RouteStop(id, order++, clean(s.place)!!, clean(s.time)?.takeIf(TIME::matches)))
            }
            stops.add(RouteStop(id, order, to!!, arriveTime))
            routes.add(
                Route(id, from!!, to!!, time.orEmpty(), clean(r.service), clean(r.routeName),
                    clean(r.contact), clean(r.info), r.longRoute == true, clean(r.startDate))
            )
        }

        if (skipped > 0) Log.w(TAG, "$skipped गलत/अधूरी एंट्री अलग रखी गईं")
        return Result(places.values.toList(), routes, stops, skipped, failedPlaces, failedRoutes)
    }
}
