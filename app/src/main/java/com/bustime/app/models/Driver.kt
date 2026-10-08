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
    val seats: Int?,             // सिर्फ़ टैक्सी के लिए (4, 5, 6, 7...)
    val fuel: String?,           // सिर्फ़ ऑटो के लिए: "ev" या "oil"
    val info: String?
)
