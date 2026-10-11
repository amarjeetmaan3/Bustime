package com.bustime.app.utils

/**
 * समय का क्रम जाँचता है और आधी रात पार होने पर दिन अपने-आप आगे बढ़ाता है।
 *
 * अगला समय पिछले से कम हो तो उसे अगले दिन का तभी माना जाता है जब दोनों के बीच का सफ़र
 * ज़्यादा से ज़्यादा 16 घंटे का बने। जैसे 23:30 -> 00:30 और 20:00 -> 10:00 ठीक हैं,
 * पर 10:00 -> 09:00 गलती है (वह 23 घंटे का सफ़र बनता)।
 * admin/index.html में भी यही नियम है; दोनों जगह एक जैसा रखना।
 */
object TimeFlow {

    const val MAX_LEG_MINUTES = 16 * 60

    /** days[i] = i-वें समय का दिन (0 = पहला दिन, 1 = अगला दिन...); खाली समय का null। badIndex = -1 मतलब सब ठीक */
    class Result(val days: List<Int?>, val badIndex: Int)

    fun toMinutes(hhmm: String?): Int? {
        if (hhmm == null) return null
        val parts = hhmm.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    fun resolve(times: List<String?>): Result {
        val days = ArrayList<Int?>()
        var last: Int? = null
        for ((i, t) in times.withIndex()) {
            val m = toMinutes(t)
            if (m == null) {
                days.add(null)
                continue
            }
            val prev = last
            var abs = m
            if (prev != null) {
                val sameDay = (prev / 1440) * 1440 + m
                if (sameDay >= prev) {
                    abs = sameDay
                } else {
                    val nextDay = sameDay + 1440
                    if (nextDay - prev > MAX_LEG_MINUTES) return Result(days, i)
                    abs = nextDay
                }
            }
            days.add(abs / 1440)
            last = abs
        }
        return Result(days, -1)
    }
}
