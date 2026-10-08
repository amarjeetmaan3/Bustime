package com.bustime.app.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/** टैक्सी के सीटर ग्रुप (4, 5, 6, 7, 8, 9...)। डेटा से बनते हैं, कोड में नहीं। */
@Entity(tableName = "seater_groups")
data class SeaterGroup(
    @PrimaryKey val seats: Int
)
