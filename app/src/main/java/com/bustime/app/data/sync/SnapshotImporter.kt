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
            if (r.hidden == true) continue
            val id = clean(r.id)
            val from = clean(r.from)
            val to = clean(r.to)
            val time = clean(r.time)?.takeIf { TIME.matches(it) }
            if (id == null || from == null || to == null || from == to ||
                !places.containsKey(from) || !places.containsKey(to) ||
                !routeIds.add(id)
            ) {
                skipped++; continue
            }
            var order = 0
            // The route departure time is also the scheduled time at the first stop.
            // Preserve it here so a time entered for the starting stand is not lost in the app.
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
                Route(id, from, to, time.orEmpty(), clean(r.service), clean(r.routeName), clean(r.contact), clean(r.info), r.longRoute == true, clean(r.startDate))
            )
        }

        // ---------- टैक्सी / ऑटो ड्राइवर (नाम और मोबाइल ज़रूरी) ----------
        val driverSeats = sortedSetOf<Int>()

        val drivers = ArrayList<Driver>()
        val driverIds = HashSet<String>()
        for (d in dto.drivers.orEmpty()) {
            if (d.hidden == true) continue
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
            // Keep seat count on both vehicle types for display, but only Taxi
            // seat counts contribute to the selectable Taxi seater filters.
            val seats: Int? = d.seats?.takeIf { it > 0 }
            if (type == "taxi") {
                if (seats == null) { skipped++; continue }   // टैक्सी में सीटर ज़रूरी
                driverSeats.add(seats)
            }
            val fuel = if (type == "auto") clean(d.fuel)?.lowercase()?.takeIf { it == "ev" || it == "oil" } else null
            // Driver area is an independent free-text service area, not a bus Place ID.
            // If an old record holds a bus-place ID, use that place's English name as the area text.
            val rawArea = clean(d.area)
            val area = rawArea?.let { a -> places[a]?.let { it.names["en"] ?: it.names.values.firstOrNull() } ?: a }
            val vehicleName = clean(d.vehicle)
            val year = clean(d.year)?.takeIf { it.matches(Regex("^[0-9]{4}$")) }
            val vehicleLabel = if (vehicleName != null && year != null) "$vehicleName · $year" else vehicleName
            drivers.add(Driver(id, type, name, phone, vehicleLabel, seats, fuel, clean(d.info), area))
        }

        // सीटर के विकल्प वही दिखेंगे जो तुमने taxiSeaters में लिखे हैं (जिस सीटर की गाड़ी नहीं, उसे सूची से हटा दो)।
        // सूची खाली या गायब हो तभी विकल्प ड्राइवरों के सीटर से बनते हैं।
        val listed = dto.taxiSeaters.orEmpty().filter { it > 0 }.toSortedSet()
        val finalSeaters: Set<Int> = if (listed.isNotEmpty()) listed else driverSeats

        if (skipped > 0) Log.w(TAG, "$skipped गलत/अधूरी एंट्री छोड़ी गईं")
        return Result(
            places.values.toList(), routes, stops, drivers,
            finalSeaters.map { SeaterGroup(it) }, skipped
        )
    }
}
