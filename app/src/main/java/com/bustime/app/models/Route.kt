package com.bustime.app.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routes_table")
data class Route(
    @PrimaryKey val id: String,
    val fromLocation: String,
    val toLocation: String,
    val vehicleType: String,       // Bus, Taxi (4 Seater), Auto (EV), etc.
    val driverOrCompanyName: String,
    val timing: String,
    val priceRange: String,        // फिक्स प्राइस की जगह प्राइस रेंज
    val driverNumber: String,      // हिडन नंबर जो AI वॉइस के बाद दिखेगा
    val updatedAt: Long            // हिडन रिफ्रेश (Delta Sync) में काम आएगा
)
