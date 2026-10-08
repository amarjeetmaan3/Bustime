package com.bustime.app.data.sync

import android.util.Log
import com.bustime.app.models.Driver
import com.bustime.app.models.Place
import com.bustime.app.models.Route
import com.bustime.app.models.RouteStop
import com.bustime.app.models.SeaterGroup
import com.bustime.app.models.SnapshotDto
import com.bustime.app.utils.TextNormalizer

/** routes.json के डेटा को जाँचकर डेटाबेस की चीज़ों में बदलता है। गलत लाइनें छोड़ देता है। */
object SnapshotImporter {

    class Result(
        val places: List<Place>,
        val routes: List<Route>,
        val stops: List<RouteStop>,
        val drivers: List<Driver>,
        val seaters: List<SeaterGroup>,
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
            val id = clean(r.id)
            val from = clean(r.from)
            val to = clean(r.to)
            val time = clean(r.time)
            if (id == null || from == null || to == null || time == null ||
                !TIME.matches(time) || from == to ||
                !places.containsKey(from) || !places.containsKey(to) ||
                !routeIds.add(id)
            ) {
                skipped++; continue
            }
            var order = 0
            stops.add(RouteStop(id, order++, from, time))
            for (s in r.stops.orEmpty()) {
                val pid = clean(s.place)
                if (pid == null || !places.containsKey(pid) || pid == from || pid == to) {
                    skipped++; continue
                }
                stops.add(RouteStop(id, order++, pid, clean(s.time)?.takeIf { TIME.matches(it) }))
            }
            stops.add(RouteStop(id, order++, to, clean(r.arrive)?.takeIf { TIME.matches(it) }))
            routes.add(
                Route(id, from, to, time, clean(r.service), clean(r.routeName), clean(r.contact), clean(r.info))
            )
        }

        // ---------- टैक्सी / ऑटो ड्राइवर (नाम और मोबाइल ज़रूरी) ----------
        val seaterSet = sortedSetOf<Int>()
        for (n in dto.taxiSeaters.orEmpty()) if (n > 0) seaterSet.add(n)

        val drivers = ArrayList<Driver>()
        val driverIds = HashSet<String>()
        for (d in dto.drivers.orEmpty()) {
            val id = clean(d.id)
            val type = clean(d.type)?.lowercase() ?: ""
            val name = clean(d.name)
            val phone = clean(d.phone)
            val digits = phone?.count { it.isDigit() } ?: 0
            if (id == null || name == null || phone == null ||
                (type != "taxi" && type != "auto") ||
                digits !in 10..13 || !driverIds.add(id)
            ) {
                skipped++; continue
            }
            var seats: Int? = null
            if (type == "taxi") {
                seats = d.seats?.takeIf { it > 0 }
                if (seats == null) { skipped++; continue }   // टैक्सी में सीटर ज़रूरी
                seaterSet.add(seats)
            }
            val fuel = if (type == "auto") clean(d.fuel)?.lowercase()?.takeIf { it == "ev" || it == "oil" } else null
            drivers.add(Driver(id, type, name, phone, clean(d.vehicle), seats, fuel, clean(d.info)))
        }

        if (skipped > 0) Log.w(TAG, "$skipped गलत/अधूरी एंट्री छोड़ी गईं")
        return Result(
            places.values.toList(), routes, stops, drivers,
            seaterSet.map { SeaterGroup(it) }, skipped
        )
    }
}
