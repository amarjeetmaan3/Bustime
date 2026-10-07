package com.bustime.app.models

data class Correction(
    val routeId: String,
    val correctedTiming: String,
    val correctedPrice: String,
    val userNotes: String,     // यूज़र का मैसेज
    val timestamp: Long        // कब सबमिट किया गया
)
