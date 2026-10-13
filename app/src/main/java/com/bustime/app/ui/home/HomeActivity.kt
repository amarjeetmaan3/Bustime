package com.bustime.app.ui.home

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import android.widget.TextView
import com.bustime.app.data.RemoteContentSettings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.tabs.TabLayout
import com.bustime.app.R
import com.bustime.app.data.repository.AppRepository
import com.bustime.app.ui.updater.GitHubUpdateChecker
import com.bustime.app.utils.LanguageHelper
import com.bustime.app.utils.AnalyticsHelper
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private var currentTab = 0
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipeStartedInContent = false
    private var swipeStartedInTaxiSeatFilters = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        AnalyticsHelper.logAppOpened(this)
        com.bustime.app.ads.CustomPopupManager.showLocalSponsorshipAd(this)

        // भाषा बदलने पर स्क्रीन दोबारा बनती है: जो टैब खुला था वही खुला रहे
        currentTab = savedInstanceState?.getInt("TAB", 0) ?: 0

        // अपडेटर चेक करना
        GitHubUpdateChecker(this).checkForLatestUpdate()

        // डेटा तुरंत sync करो और ऐप foreground में रहने तक हर 10 सेकंड में दोबारा जाँचो।
        // इंटरनेट लौटने पर अगली जाँच अपने-आप होगी; offline में पुराना डेटा सुरक्षित रहता है।
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val repository = AppRepository.getInstance(this@HomeActivity)
                while (true) {
                    val changed = repository.syncData()
                    if (changed && supportFragmentManager.findFragmentById(R.id.fragmentContainer) != null) {
                        refreshCurrentTab()
                    }
                    kotlinx.coroutines.delay(10_000)
                }
            }
        }

        // ऊपर "भाषा" का बटन
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.inflateMenu(R.menu.home_menu)
        updateLanguageIndicator(toolbar)
        loadWarningTicker()
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_language -> { showLanguageDialog(); true }
                R.id.action_refresh -> { refreshData(toolbar); true }
                R.id.action_feedback -> {
                    startActivity(android.content.Intent(this, com.bustime.app.ui.community.CommunityRequestActivity::class.java).putExtra("mode", "feedback")); true
                }
                else -> false
            }
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

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartX = event.rawX
                swipeStartY = event.rawY
                val container = findViewById<View>(R.id.fragmentContainer)
                if (container != null) {
                    val location = IntArray(2)
                    container.getLocationOnScreen(location)
                    swipeStartedInContent = event.rawX >= location[0] &&
                        event.rawX <= location[0] + container.width &&
                        event.rawY >= location[1] &&
                        event.rawY <= location[1] + container.height
                } else {
                    swipeStartedInContent = false
                }
                val seatFilters = findViewById<View>(R.id.seatFiltersScroll)
                swipeStartedInTaxiSeatFilters = false
                if (seatFilters != null && seatFilters.visibility == View.VISIBLE) {
                    val filterLocation = IntArray(2)
                    seatFilters.getLocationOnScreen(filterLocation)
                    swipeStartedInTaxiSeatFilters =
                        event.rawX >= filterLocation[0] &&
                        event.rawX <= filterLocation[0] + seatFilters.width &&
                        event.rawY >= filterLocation[1] &&
                        event.rawY <= filterLocation[1] + seatFilters.height
                }
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.rawX - swipeStartX
                val dy = event.rawY - swipeStartY
                val minSwipe = 72 * resources.displayMetrics.density
                val shouldSwitch = swipeStartedInContent && !swipeStartedInTaxiSeatFilters &&
                    kotlin.math.abs(dx) >= minSwipe &&
                    kotlin.math.abs(dy) < minSwipe
                val handled = super.dispatchTouchEvent(event)
                swipeStartedInContent = false
                swipeStartedInTaxiSeatFilters = false
                if (shouldSwitch) {
                    val nextTab = currentTab + if (dx < 0) 1 else -1
                    if (nextTab in 0..2) {
                        findViewById<TabLayout>(R.id.tabLayout).getTabAt(nextTab)?.select()
                    }
                }
                return handled
            }
            MotionEvent.ACTION_CANCEL -> { swipeStartedInContent = false; swipeStartedInTaxiSeatFilters = false }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("TAB", currentTab)
    }

    private fun refreshData(toolbar: Toolbar) {
        val refreshItem = toolbar.menu.findItem(R.id.action_refresh) ?: return
        if (!refreshItem.isEnabled) return
        refreshItem.isEnabled = false
        val density = resources.displayMetrics.density
        val progress = ProgressBar(this).apply {
            isIndeterminate = true
            contentDescription = getString(R.string.refresh_started)
        }
        val holder = FrameLayout(this).apply {
            val size = (48 * density).toInt()
            layoutParams = android.view.ViewGroup.LayoutParams(size, size)
            addView(progress, FrameLayout.LayoutParams(
                (24 * density).toInt(), (24 * density).toInt(), Gravity.CENTER
            ))
        }
        refreshItem.actionView = holder
        Toast.makeText(this, R.string.refresh_started, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                AppRepository.getInstance(this@HomeActivity).syncData()
                refreshCurrentTab()
                loadWarningTicker()
            } catch (e: Exception) {
                Toast.makeText(this@HomeActivity, "Refresh failed: " + (e.message ?: "Please try again"), Toast.LENGTH_LONG).show()
            } finally {
                refreshItem.actionView = null
                refreshItem.setIcon(R.drawable.ic_refresh)
                refreshItem.isEnabled = true
            }
        }
    }

    private fun refreshCurrentTab() {
        when (currentTab) {
            1 -> replaceFragment(TaxiFragment())
            2 -> replaceFragment(AutoRickshawFragment())
            else -> replaceFragment(BusFragment())
        }
    }

    private fun updateLanguageIndicator(toolbar: Toolbar) {
        toolbar.menu.findItem(R.id.action_language)?.title = ""
    }

    private fun loadWarningTicker() {
        lifecycleScope.launch {
            val settings = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                RemoteContentSettings.refresh()
            }
            val ticker = findViewById<TextView>(R.id.warningTicker)
            if (!settings.tickerEnabled) {
                ticker.visibility = View.GONE
                return@launch
            }
            val langs = RemoteContentSettings.pairedLanguages(LanguageHelper.current())
            val pieces = langs.mapNotNull { lang -> settings.ticker[lang]?.trim()?.takeIf { it.isNotEmpty() } }
            if (pieces.isEmpty()) {
                ticker.visibility = View.GONE
                return@launch
            }
            ticker.text = ("     " + pieces.joinToString("     •     ") + "     •     ").repeat(4)
            ticker.visibility = View.VISIBLE
            ticker.isSelected = true
            ticker.post { ticker.requestFocus() }
        }
    }

    private fun showLanguageDialog() {
        val tags = arrayOf("en", "hi", "pa")
        val names = arrayOf("English", "हिन्दी", "ਪੰਜਾਬੀ")
        val selected = tags.indexOf(LanguageHelper.current()).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.language_title)
            .setSingleChoiceItems(names, selected) { dialog, which ->
                // भाषा बदलते ही स्क्रीन अपने-आप नई भाषा में खुल जाती है
                val selectedTag = tags[which]
                dialog.dismiss()
                if (selectedTag != LanguageHelper.current()) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(selectedTag))
                    // Suppress the visible close/open transition during locale recreation.
                    overridePendingTransition(0, 0)
                }
            }
            .show()
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
