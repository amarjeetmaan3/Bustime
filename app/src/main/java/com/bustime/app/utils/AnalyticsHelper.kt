package com.bustime.app.utils

import android.content.Context
import android.util.Log

object AnalyticsHelper {
    
    fun logAppOpened(context: Context) {
        // यहाँ हम कस्टम API या एनालिटिक्स सर्विस को हिट करेंगे
        // ताकि एडमिन पैनल में "Daily Views" अपडेट हो सके
        Log.d("AnalyticsHelper", "App Opened event triggered. Counting view...")
    }

    fun logSearchEvent(from: String, to: String, vehicleType: String) {
        // कौनसे रूट सबसे ज्यादा सर्च हो रहे हैं, उसका डेटा रखने के लिए
        Log.d("AnalyticsHelper", "Search Logged: $from to $to by $vehicleType")
    }
}
