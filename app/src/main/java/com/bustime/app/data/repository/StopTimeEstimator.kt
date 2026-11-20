package com.bustime.app.data.repository

import com.bustime.app.models.RouteStop

/**
 * जिस पड़ाव का समय खाली है, वहाँ बस कब पहुँचेगी — इसका अंदाज़ा लगाता है।
 *
 * यह अंदाज़ा सिर्फ़ नतीजों का क्रम तय करने के लिए है। स्क्रीन पर खाली समय खाली ही दिखता है।
 *
 * अंदाज़ा ऐसे लगता है (उस रूट के भरे हुए समयों को "anchor" कहते हैं):
 *  1. पड़ाव का अपना समय भरा हो तो वही लिया जाता है।
 *  2. दोनों तरफ़ anchor हों तो उनके बीच अंदाज़ा लगाया जाता है। दूसरे रूट से पड़ावों के बीच का
 *     असली समय पता हो तो उसका अनुपात लिया जाता है, नहीं तो पड़ावों की गिनती के हिसाब से।
 *  3. सिर्फ़ एक तरफ़ anchor हो तो उस anchor का समय लेकर दूसरे रूट से सीखा हुआ फ़र्क जोड़ा/घटाया
 *     जाता है। वह न मिले तो ऐप के सारे रूटों का औसत "मिनट प्रति पड़ाव" इस्तेमाल होता है।
 *  4. रूट में कोई समय ही न हो तो null — ऐसे नतीजे आख़िर में आते हैं।
 */
class StopTimeEstimator(allStops: List<RouteStop>) {

    private class Stop(val order: Int, val placeId: String, val minutes: Int?)

    private val routes: Map<String, List<Stop>> = allStops
        .groupBy { it.routeId }
        .mapValues { entry ->
            entry.value
                .sortedBy { it.stopOrder }
                .map { Stop(it.stopOrder, it.placeId, toMinutes(it.time)) }
        }

    private val durationCache = HashMap<String, Int?>()

    /** सारे रूटों का औसत (बीच का) मिनट प्रति पड़ाव; पता न चले तो null */
    private val minutesPerStop: Double? by lazy {
        val samples = ArrayList<Double>()
        for (stops in routes.values) {
            val timed = stops.filter { it.minutes != null }
            if (timed.size < 2) continue
            val first = timed.first()
            val last = timed.last()
            var diff = last.minutes!! - first.minutes!!
            if (diff < 0) diff += DAY
            val count = last.order - first.order
            if (count > 0 && diff in 1..MAX_SEGMENT) samples.add(diff.toDouble() / count)
        }
        if (samples.isEmpty()) null else {
            samples.sort()
            (samples[(samples.size - 1) / 2] + samples[samples.size / 2]) / 2.0
        }
    }

    /**
     * routeId वाले रूट में placeId वाले पड़ाव पर पहुँचने का समय, मिनटों में (0..1439)।
     * ownTime भरा हो तो वही लौटता है। beforePlaceId दिया हो (From→To सर्च) तो वही पड़ाव चुना जाता है
     * जो उस जगह से पहले आता है। अंदाज़ा न लग सके तो null.
     */
    fun estimateAt(routeId: String, placeId: String, ownTime: String?, beforePlaceId: String? = null): Int? {
        val own = toMinutes(ownTime)
        if (own != null) return own

        val stops = routes[routeId] ?: return null
        val limit = if (beforePlaceId == null) {
            Int.MAX_VALUE
        } else {
            stops.lastOrNull { it.placeId == beforePlaceId }?.order ?: Int.MAX_VALUE
        }
        val target = stops.firstOrNull { it.placeId == placeId && it.minutes == null && it.order < limit }
            ?: return null

        val prev = stops.lastOrNull { it.order < target.order && it.minutes != null }
        val next = stops.firstOrNull { it.order > target.order && it.minutes != null }

        val raw: Int = when {
            prev != null && next != null -> between(prev, target, next)
            prev != null -> {
                val gap = duration(prev.placeId, target.placeId)
                    ?: guessFromStops(target.order - prev.order)
                prev.minutes!! + gap
            }
            next != null -> {
                val gap = duration(target.placeId, next.placeId)
                    ?: guessFromStops(next.order - target.order)
                next.minutes!! - gap
            }
            else -> return null
        }
        return ((raw % DAY) + DAY) % DAY
    }

    private fun between(prev: Stop, target: Stop, next: Stop): Int {
        val start = prev.minutes!!
        var end = next.minutes!!
        if (end < start) end += DAY
        val span = end - start

        val first = duration(prev.placeId, target.placeId)
        val second = duration(target.placeId, next.placeId)
        if (first != null && second != null && first + second > 0) {
            return start + (span.toLong() * first / (first + second)).toInt()
        }
        val count = next.order - prev.order
        return start + span * (target.order - prev.order) / count
    }

    private fun guessFromStops(stopCount: Int): Int {
        val rate = minutesPerStop ?: return 0
        return (rate * stopCount).toInt()
    }

    /**
     * दूसरे रूटों से सीखा हुआ: from जगह से to जगह तक बस को आम तौर पर कितने मिनट लगते हैं
     * (उन रूटों के बीच का मान जिनमें दोनों जगह का समय भरा है)। न मिले तो null.
     */
    private fun duration(from: String, to: String): Int? {
        val key = "$from>$to"
        if (durationCache.containsKey(key)) return durationCache[key]

        val samples = ArrayList<Int>()
        for (stops in routes.values) {
            val a = stops.firstOrNull { it.placeId == from && it.minutes != null } ?: continue
            val b = stops.firstOrNull { it.placeId == to && it.minutes != null && it.order > a.order } ?: continue
            var diff = b.minutes!! - a.minutes!!
            if (diff < 0) diff += DAY
            if (diff <= MAX_SEGMENT) samples.add(diff)
        }
        val result: Int? = if (samples.isEmpty()) null else {
            samples.sort()
            (samples[(samples.size - 1) / 2] + samples[samples.size / 2]) / 2
        }
        durationCache[key] = result
        return result
    }

    private fun toMinutes(time: String?): Int? {
        if (time == null || time.length != 5 || time[2] != ':') return null
        val h = time.substring(0, 2).toIntOrNull() ?: return null
        val m = time.substring(3, 5).toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    private companion object {
        const val DAY = 1440
        const val MAX_SEGMENT = 720   // दो पड़ावों के बीच 12 घंटे से ज़्यादा का फ़र्क गलत डेटा माना जाता है
    }
}
