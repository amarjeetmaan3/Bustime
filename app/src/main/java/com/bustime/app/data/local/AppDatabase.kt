package com.bustime.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.TypeConverters
import com.bustime.app.models.Place
import com.bustime.app.models.Route
import com.bustime.app.models.RouteStop

@Database(
    entities = [Place::class, Route::class, RouteStop::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transportDao(): TransportDao

    companion object {
        // v4 contained legacy Taxi/Auto tables. Drop only those tables and preserve
        // all Bus tables (places, routes, route_stops) during the upgrade.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS drivers")
                db.execSQL("DROP TABLE IF EXISTS seater_groups")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bustime_offline_database"
                )
                    .addMigrations(MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
