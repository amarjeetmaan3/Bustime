package com.bustime.app.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, String>>() {}.type

    @TypeConverter
    fun fromMap(map: Map<String, String>): String = gson.toJson(map)

    @TypeConverter
    fun toMap(json: String): Map<String, String> =
        gson.fromJson<Map<String, String>>(json, mapType) ?: emptyMap()
}
