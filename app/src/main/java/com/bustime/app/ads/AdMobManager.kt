package com.bustime.app.ads

import android.content.Context
import android.util.Log
import com.bustime.app.utils.NetworkGuard

object AdMobManager {
    
    fun initialize(context: Context) {
        if (NetworkGuard.isInternetAvailable(context)) {
            // MobileAds.initialize(context) {} -> (Dependency ऐड करने के बाद अनकमेंट करेंगे)
            Log.d("AdMobManager", "AdMob Initialized Successfully.")
        } else {
            Log.e("AdMobManager", "No Internet! Ads blocked. NetworkGuard will show lock screen.")
        }
    }
}
