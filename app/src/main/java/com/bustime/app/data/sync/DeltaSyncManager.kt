package com.bustime.app.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.bustime.app.data.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DeltaSyncManager(
    private val context: Context,
    private val repository: AppRepository
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
    
    fun performHiddenRefresh() {
        // बैकग्राउंड थ्रेड में बिना ऐप रोके सिंक चलाना
        CoroutineScope(Dispatchers.IO).launch {
            val lastSyncTime = prefs.getLong("LAST_SYNC_TIME", 0L)
            
            // रिपॉजिटरी से नया डेटा सिंक करवाना
            repository.syncRoutes(lastSyncTime)
            
            // सिंक पूरा होने के बाद नया टाइम सेव कर लेना
            prefs.edit().putLong("LAST_SYNC_TIME", System.currentTimeMillis()).apply()
        }
    }
}
