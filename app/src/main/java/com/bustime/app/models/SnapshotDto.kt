package com.bustime.app.models

// routes.json को पढ़ने के लिए। हर फ़ील्ड nullable है ताकि अधूरा डेटा ऐप को क्रैश न करे।

data class SnapshotDto(
    val dataVersion: Int?,
    val places: List<PlaceDto>?,
    val routes: List<RouteDto>?
)

data class PlaceDto(
    val id: String?,
    val kind: String?,
    val name: Map<String, String>?,
    val aliases: List<String>?,
    val city: String?,
    val district: String?
)

data class RouteDto(
    val id: String?,
    val from: String?,
    val to: String?,
    val time: String?,
    val arrive: String?,
    val service: String?,
    val routeName: String?,
    val contact: String?,
    val info: String?,
    val stops: List<StopDto>?,
    val longRoute: Boolean?,
    val startDate: String?,
    val hidden: Boolean?
)

data class StopDto(
    val place: String?,
    val time: String?
)
