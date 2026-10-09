package com.bustime.app.ui.home

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.bustime.app.R
import com.bustime.app.data.repository.AppRepository
import com.bustime.app.ui.updater.GitHubUpdateChecker
import com.bustime.app.utils.LanguageHelper
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private var currentTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // भाषा बदलने पर स्क्रीन दोबारा बनती है: जो टैब खुला था वही खुला रहे
        currentTab = savedInstanceState?.getInt("TAB", 0) ?: 0

        // अपडेटर चेक करना
        GitHubUpdateChecker(this).checkForLatestUpdate()

        // रूट/ड्राइवर का डेटा: पहली बार भरना, फिर बदलाव देखना (बैकग्राउंड में)
        lifecycleScope.launch { AppRepository.getInstance(this@HomeActivity).syncData() }

        // ऊपर "भाषा" का बटन
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.inflateMenu(R.menu.home_menu)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_language) {
                showLanguageDialog()
                true
            } else false
        }

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        tabLayout.getTabAt(currentTab)?.select()
        if (savedInstanceState == null) {
            replaceFragment(BusFragment())
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                when (currentTab) {
                    0 -> replaceFragment(BusFragment())
                    1 -> replaceFragment(TaxiFragment())
                    2 -> replaceFragment(AutoRickshawFragment())
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("TAB", currentTab)
    }

    private fun showLanguageDialog() {
        val tags = arrayOf("en", "hi", "pa")
        val names = arrayOf("English", "हिन्दी", "ਪੰਜਾਬੀ")
        val selected = tags.indexOf(LanguageHelper.current()).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.language_title)
            .setSingleChoiceItems(names, selected) { dialog, which ->
                // भाषा बदलते ही स्क्रीन अपने-आप नई भाषा में खुल जाती है
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags[which]))
                dialog.dismiss()
            }
            .show()
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
