package com.bustime.app.ui.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayout
import com.bustime.app.R
import com.bustime.app.ads.AdMobManager
import com.bustime.app.ui.updater.GitHubUpdateChecker
import com.bustime.app.utils.AnalyticsHelper
import com.bustime.app.utils.NetworkGuard

class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!NetworkGuard.isInternetAvailable(this)) {
            setContentView(R.layout.layout_network_error)
            return
        }

        setContentView(R.layout.activity_home)

        AnalyticsHelper.logAppOpened(this)
        AdMobManager.initialize(this)
        GitHubUpdateChecker(this).checkForLatestUpdate()

        // टैब लेआउट को ढूँढना
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)

        // ऐप खुलते ही डिफ़ॉल्ट रूप से Bus वाला टैब दिखाना
        replaceFragment(BusFragment())

        // जब यूज़र किसी टैब पर क्लिक करे
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> replaceFragment(BusFragment())           // पहला टैब (Bus)
                    1 -> replaceFragment(TaxiFragment())          // दूसरा टैब (Taxi)
                    2 -> replaceFragment(AutoRickshawFragment())  // तीसरा टैब (Auto)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    // स्क्रीन के बीच वाले हिस्से (FrameLayout) में नई स्क्रीन (Fragment) चिपकाने का फंक्शन
    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
