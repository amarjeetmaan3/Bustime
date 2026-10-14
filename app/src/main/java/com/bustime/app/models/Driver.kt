package com.bustime.app.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/** टैक्सी और ऑटो: नाम और मोबाइल नंबर ज़रूरी हैं। */
@Entity(tableName = "drivers")
data class Driver(
    @PrimaryKey val id: String,
    val type: String,            // "taxi" या "auto"
    val name: String,
    val phone: String,
    val vehicle: String?,        // गाड़ी की डिटेल
    val seats: Int?,             // Taxi: filter key; Auto: informational display only
    val fuel: String?,           // सिर्फ़ ऑटो के लिए: "ev" या "oil"
    val info: String?,
    val areaId: String? = null   // वह शहर/इलाका जहाँ यह गाड़ी मिलती है (जगह की id)
)
