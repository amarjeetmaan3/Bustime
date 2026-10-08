package com.bustime.app.ui.search

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bustime.app.utils.AnalyticsHelper

class SearchActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setContentView(R.layout.activity_search) -> UI बनने के बाद

        val fromLocation = intent.getStringExtra("FROM") ?: ""
        val toLocation = intent.getStringExtra("TO") ?: ""
        val vehicleType = intent.getStringExtra("TYPE") ?: "Bus"

        // सर्च एनालिटिक्स दर्ज करें
        AnalyticsHelper.logSearchEvent(fromLocation, toLocation, vehicleType)

                // TODO (चरण 3): AppRepository से सर्च करके नतीजे दिखाना
    }
}
