package com.bustime.app.data.repository

import com.bustime.app.data.local.RouteDao
import com.bustime.app.data.remote.ApiService
import com.bustime.app.models.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(
    private val routeDao: RouteDao,
    private val apiService: ApiService
) {
    // सर्च के समय डेटा सीधे लोकल डेटाबेस से आएगा (सुपरफास्ट)
    suspend fun searchLocalRoutes(from: String, to: String): List<Route> {
        return routeDao.searchRoutes(from, to)
    }

    // हिडन रिफ्रेश के दौरान नया डेटा API से लाकर लोकल DB में डालना
    suspend fun syncRoutes(lastUpdated: Long) {
        withContext(Dispatchers.IO) {
            try {
                // 1. नए या अपडेट हुए रूट्स लाकर सेव करना
                val updatedRoutes = apiService.getUpdatedRoutes(lastUpdated)
                if (updatedRoutes.isNotEmpty()) {
                    routeDao.insertRoutes(updatedRoutes)
                }
                
                // 2. एडमिन द्वारा डिलीट किए गए रूट्स को हटाना
                val activeIds = apiService.getActiveRouteIds()
                if (activeIds.isNotEmpty()) {
                    routeDao.removeDeletedRoutes(activeIds)
                }
            } catch (e: Exception) {
                // अगर नेटवर्क एरर आए, तो कुछ नहीं करना (यूज़र को पता नहीं चलेगा)
                e.printStackTrace()
            }
        }
    }
}
