package com.bustime.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.bustime.app.models.Driver
import com.bustime.app.models.Place
import com.bustime.app.models.Route
import com.bustime.app.models.RouteStop
import com.bustime.app.models.SeaterGroup

@Database(
    entities = [Place::class, Route::class, RouteStop::class, Driver::class, SeaterGroup::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transportDao(): TransportDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bustime_offline_database"
                )
                    // डेटा हमेशा routes.json से दोबारा आ सकता है, इसलिए पुराना ढाँचा बदलते समय मिटा देना ठीक है
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
