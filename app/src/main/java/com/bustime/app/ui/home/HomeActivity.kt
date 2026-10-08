package com.bustime.app.ui.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.bustime.app.R
import com.bustime.app.data.repository.AppRepository
import com.bustime.app.ui.updater.GitHubUpdateChecker
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // अपडेटर चेक करना
        GitHubUpdateChecker(this).checkForLatestUpdate()

        // रूट/ड्राइवर का डेटा: पहली बार भरना, फिर बदलाव देखना (बैकग्राउंड में)
        lifecycleScope.launch { AppRepository.getInstance(this@HomeActivity).syncData() }

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        replaceFragment(BusFragment())

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> replaceFragment(BusFragment())
                    1 -> replaceFragment(TaxiFragment())
                    2 -> replaceFragment(AutoRickshawFragment())
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
