package com.bustime.app.models

import androidx.room.Entity
import androidx.room.Index

/** रूट के सारे पड़ाव क्रम से (शुरू और अंत भी शामिल)। From→To और बस स्टैंड सर्च इसी से चलते हैं। */
@Entity(
    tableName = "route_stops",
    primaryKeys = ["routeId", "stopOrder"],
    indices = [Index("placeId")]
)
data class RouteStop(
    val routeId: String,
    val stopOrder: Int,
    val placeId: String,
    val time: String?            // इस पड़ाव का समय "HH:mm" (न पता हो तो खाली)
)
