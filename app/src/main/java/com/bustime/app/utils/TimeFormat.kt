package com.bustime.app.utils

object TimeFormat {
    /** "06:30" -> "6:30 AM"; समय न हो तो "--" */
    fun format(hhmm: String?): String {
        if (hhmm == null) return "--"
        val parts = hhmm.split(":")
        val h = parts[0].toIntOrNull() ?: return hhmm
        val m = parts.getOrNull(1) ?: "00"
        val h12 = if (h % 12 == 0) 12 else h % 12
        val suffix = if (h < 12) "AM" else "PM"
        return "$h12:$m $suffix"
    }
}
