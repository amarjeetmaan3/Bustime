package com.bustime.app.data.sync

import android.util.Log
import com.bustime.app.models.Place
import com.bustime.app.models.Route
import com.bustime.app.models.RouteStop
import com.bustime.app.models.SnapshotDto
import com.bustime.app.utils.TextNormalizer

/** routes.json के डेटा को जाँचकर डेटाबेस की चीज़ों में बदलता है। गलत लाइनें छोड़ देता है। */
object SnapshotImporter {

    class Result(
        val places: List<Place>,
        val routes: List<Route>,
        val stops: List<RouteStop>,
        val skipped: Int
    )

    private const val TAG = "SnapshotImporter"
    private val TIME = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

    private fun clean(s: String?): String? = s?.trim()?.takeIf { it.isNotEmpty() }

    fun build(dto: SnapshotDto): Result {
        var skipped = 0

        // ---------- जगहें ----------
        val places = LinkedHashMap<String, Place>()
        for (p in dto.places.orEmpty()) {
            val id = clean(p.id)
            val names = LinkedHashMap<String, String>()
            for ((k, v) in p.name.orEmpty()) {
                val key: String? = k
                val value: String? = v
                if (!key.isNullOrBlank() && !value.isNullOrBlank()) {
                    names[key.trim().lowercase()] = value.trim()
                }
            }
            if (id == null || names.isEmpty() || places.containsKey(id)) {
                skipped++; continue
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

        // ---------- बस रूट और उनके पड़ाव ----------
        val routes = ArrayList<Route>()
        val stops = ArrayList<RouteStop>()
        val routeIds = HashSet<String>()
        for (r in dto.routes.orEmpty()) {
            if (r.hidden == true) continue
            val id = clean(r.id)
            val from = clean(r.from)
            val to = clean(r.to)
            val startTime = clean(r.time)?.takeIf { TIME.matches(it) }
            val arriveTime = clean(r.arrive)?.takeIf { TIME.matches(it) }
            val intermediateTimes = r.stops.orEmpty().mapNotNull { clean(it.time)?.takeIf { t -> TIME.matches(t) } }
            val time = startTime ?: intermediateTimes.firstOrNull() ?: arriveTime
            if (id == null || from == null || to == null || from == to ||
                !places.containsKey(from) || !places.containsKey(to) ||
                !routeIds.add(id)
            ) {
                skipped++; continue
            }
            var order = 0
            // The route departure time is also the scheduled time at the first stop.
            // Preserve it here so a time entered for the starting stand is not lost in the app.
            stops.add(RouteStop(id, order++, from, startTime))
            for (s in r.stops.orEmpty()) {
                val pid = clean(s.place)
                if (pid == null || !places.containsKey(pid) || pid == from || pid == to) {
                    skipped++; continue
                }
                stops.add(RouteStop(id, order++, pid, clean(s.time)?.takeIf { TIME.matches(it) }))
            }
            stops.add(RouteStop(id, order++, to, arriveTime))
            routes.add(
                Route(id, from, to, time.orEmpty(), clean(r.service), clean(r.routeName), clean(r.contact), clean(r.info), r.longRoute == true, clean(r.startDate))
            )
        }


        if (skipped > 0) Log.w(TAG, "$skipped गलत/अधूरी एंट्री छोड़ी गईं")
        return Result(
            places.values.toList(), routes, stops, skipped
        )
    }
}
