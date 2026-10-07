package com.bustime.app.models

data class VehicleInfo(
    val vehicleId: String,
    val type: String,          // Taxi (4 Seater), Auto (EV), etc.
    val isAcAvailable: Boolean,
    val totalSeats: Int,
    val additionalFeatures: String
)
