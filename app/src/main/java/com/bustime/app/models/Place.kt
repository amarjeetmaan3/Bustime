package com.bustime.app.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/** शहर / बस स्टैंड / कोई भी जगह। नाम कई भाषाओं में रहते हैं: {"en": "...", "hi": "...", "pa": "..."} */
@Entity(tableName = "places")
data class Place(
    @PrimaryKey val id: String,
    val kind: String,                  // "stand" (बस स्टैंड) या "place"
    val names: Map<String, String>,
    val city: String?,
    val district: String?,
    val searchText: String             // सारे नाम + उपनाम, साफ़ किए हुए (सर्च के लिए)
) {
    // चुनी हुई भाषा का नाम; न मिले तो अंग्रेज़ी, फिर जो भी हो
    fun displayName(lang: String): String =
        names[lang]?.takeIf { it.isNotBlank() }
            ?: names["en"]?.takeIf { it.isNotBlank() }
            ?: names.values.firstOrNull { it.isNotBlank() }.orEmpty()
}
