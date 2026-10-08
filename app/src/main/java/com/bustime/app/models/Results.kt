package com.bustime.app.models

/** From → To सर्च का एक नतीजा */
data class BusResult(
    val routeId: String,
    val routeFromId: String,
    val routeToId: String,
    val serviceName: String?,
    val routeName: String?,
    val contact: String?,
    val info: String?,
    val fromTime: String?,   // From वाली जगह का समय
    val toTime: String?      // To वाली जगह का समय
)

/** बस स्टैंड सर्च का एक नतीजा */
data class StandResult(
    val routeId: String,
    val routeFromId: String,
    val routeToId: String,
    val serviceName: String?,
    val routeName: String?,
    val contact: String?,
    val info: String?,
    val atTime: String?      // इस स्टैंड पर बस का समय
)
