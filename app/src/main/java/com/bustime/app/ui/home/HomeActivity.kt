package com.bustime.app.ui.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bustime.app.ads.AdMobManager
import com.bustime.app.updater.GitHubUpdateChecker
import com.bustime.app.utils.AnalyticsHelper
import com.bustime.app.utils.NetworkGuard

class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. सबसे पहले इंटरनेट चेक करें
        if (!NetworkGuard.isInternetAvailable(this)) {
            setContentView(com.bustime.app.R.layout.layout_network_error) // नेटवर्क लॉक स्क्रीन
            return
        }

        setContentView(com.bustime.app.R.layout.activity_home)

        // 2. व्यू काउंट दर्ज करें
        AnalyticsHelper.logAppOpened(this)

        // 3. AdMob इनिशियलाइज़ करें
        AdMobManager.initialize(this)

        // 4. GitHub से नया वर्ज़न चेक करें
        GitHubUpdateChecker(this).checkForLatestUpdate()

        // 5. कस्टम पॉप-अप (लोकल स्पॉन्सरशिप) और बैकग्राउंड सिंक यहाँ कॉल होंगे
        // TODO: Setup Tabs for Bus, Taxi (4/5/7 Seater), Auto (EV/Oil)
    }
}
