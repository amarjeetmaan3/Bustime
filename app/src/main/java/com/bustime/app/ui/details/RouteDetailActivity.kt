package com.bustime.app.ui.details

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class RouteDetailActivity : AppCompatActivity() {
    
    private lateinit var contactHelper: ContactDriverHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setContentView(R.layout.activity_route_detail)

        contactHelper = ContactDriverHelper(this)

        val driverNumber = intent.getStringExtra("DRIVER_NUMBER") ?: ""

        // जब यूज़र 'Show Number' बटन पर टच करेगा
        // contactHelper.playDisclaimerAndShowNumber(driverNumber) { ... }
    }
}
