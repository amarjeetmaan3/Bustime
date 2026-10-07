package com.bustime.app.data.remote

import com.bustime.app.models.Route
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {
    // सिर्फ उसी समय के बाद वाले रूट्स लाएगा जब आखिरी बार रिफ्रेश हुआ था (Delta Sync)
    @GET("api/routes/sync")
    suspend fun getUpdatedRoutes(@Query("lastUpdated") lastUpdated: Long): List<Route>
    
    // डेटाबेस से डिलीट हुए रूट्स को लोकल कैशे से भी हटाने के लिए एक्टिव IDs की लिस्ट
    @GET("api/routes/active_ids")
    suspend fun getActiveRouteIds(): List<String>
}
