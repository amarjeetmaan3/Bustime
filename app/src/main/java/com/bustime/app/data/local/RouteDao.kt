package com.bustime.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bustime.app.models.Route

@Dao
interface RouteDao {
    // हिडन रिफ्रेश के समय नया डेटा सेव या अपडेट करने के लिए
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutes(routes: List<Route>)

    // यूज़र द्वारा सर्च किए गए रूट का डेटा ऑफलाइन कैशे से निकालने के लिए
    @Query("SELECT * FROM routes_table WHERE fromLocation = :from AND toLocation = :to")
    suspend fun searchRoutes(from: String, to: String): List<Route>

    // हिडन रिफ्रेश के दौरान अगर एडमिन ने कोई रूट डिलीट किया है, तो उसे यहाँ से भी हटाने के लिए
    @Query("DELETE FROM routes_table WHERE id NOT IN (:activeRouteIds)")
    suspend fun removeDeletedRoutes(activeRouteIds: List<String>)
}
