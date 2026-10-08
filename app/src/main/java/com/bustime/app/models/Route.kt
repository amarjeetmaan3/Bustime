package com.bustime.app.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/** बस रूट: पहचान From + To + समय है, बस नंबर नहीं। नंबर/ड्राइवर का नाम ज़रूरी नहीं। */
@Entity(tableName = "routes")
data class Route(
    @PrimaryKey val id: String,
    val fromId: String,
    val toId: String,
    val time: String,            // शुरू होने का समय "HH:mm" (24 घंटे)
    val serviceName: String?,    // सर्विस / कंपनी का नाम (वैकल्पिक)
    val routeName: String?,      // वैकल्पिक
    val contact: String?,        // वैकल्पिक
    val info: String?            // वैकल्पिक
)
