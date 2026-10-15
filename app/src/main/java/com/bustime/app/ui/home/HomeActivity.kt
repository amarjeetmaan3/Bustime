package com.bustime.app.ui.home

import android.os.Bundle
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.bustime.app.R
import com.bustime.app.data.repository.AppRepository
import com.bustime.app.ui.updater.GitHubUpdateChecker
import com.bustime.app.utils.LanguageHelper
import com.bustime.app.utils.AnalyticsHelper
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bustime.app.data.sync.DataSyncWorker
import com.bustime.app.utils.Constants
import java.util.concurrent.TimeUnit

class HomeActivity : AppCompatActivity() {

    private var syncReceiverRegistered = false
    private val syncReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(DataSyncWorker.KEY_PENDING_UI_REFRESH, false).apply()
            if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) != null) {
                refreshCurrentTab()
            }
        }
    }

    private var currentTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        scheduleBackgroundDataSync()
        AnalyticsHelper.logAppOpened(this)
        com.bustime.app.ads.CustomPopupManager.showLocalSponsorshipAd(this)

        // भाषा बदलने पर स्क्रीन दोबारा बनती है: जो टैब खुला था वही खुला रहे
        currentTab = 0

        // अपडेटर चेक करना
        GitHubUpdateChecker(this).checkForLatestUpdate()

        // ऐप खुलने / वापस foreground में आने पर quietly check for new data.
        // कोई polling loop नहीं; पुराना local data offline में उपलब्ध रहता है.

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
                // Bustime is now focused exclusively on bus routes and Book Bus.
                // Keep one tab so old saved tab indices cannot open removed vehicle screens.
                replaceFragment(BusFragment())
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    override fun onStart() {
        super.onStart()
        if (!syncReceiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                syncReceiver,
                IntentFilter(DataSyncWorker.ACTION_DATA_SYNCED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            syncReceiverRegistered = true
        }
    }

    override fun onStop() {
        if (syncReceiverRegistered) {
            unregisterReceiver(syncReceiver)
            syncReceiverRegistered = false
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val changed = AppRepository.getInstance(this@HomeActivity).syncData()
            val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
            val pendingUiRefresh = prefs.getBoolean(DataSyncWorker.KEY_PENDING_UI_REFRESH, false)
            if ((changed || pendingUiRefresh) && !isFinishing &&
                supportFragmentManager.findFragmentById(R.id.fragmentContainer) != null) {
                prefs.edit().putBoolean(DataSyncWorker.KEY_PENDING_UI_REFRESH, false).apply()
                refreshCurrentTab()
            }
        }
        // Marquee can miss its first start while the window is still gaining focus.
        // Re-arm it after layout whenever the app returns to the foreground.
        findViewById<TextView>(R.id.warningTicker).post {
            val ticker = findViewById<TextView>(R.id.warningTicker)
            if (ticker.visibility == View.VISIBLE && ticker.text.isNotBlank()) {
                ticker.isSelected = false
                ticker.isSelected = true
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("TAB", currentTab)
    }


    private fun scheduleBackgroundDataSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<DataSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "bustime_periodic_data_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
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
                val repository = AppRepository.getInstance(this@HomeActivity)
                val changed = repository.syncData()
                refreshCurrentTab()
                loadWarningTicker()
                val message = when {
                    repository.didLastSyncFail() -> R.string.refresh_failed
                    changed -> R.string.refresh_complete
                    else -> R.string.refresh_latest
                }
                Toast.makeText(this@HomeActivity, message, Toast.LENGTH_SHORT).show()
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
        val item = toolbar.menu.findItem(R.id.action_language) ?: return
        val languageMark = TextView(this).apply {
            text = "A ਅ अ"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 15f
            minWidth = (68 * resources.displayMetrics.density).toInt()
            layoutParams = android.widget.FrameLayout.LayoutParams(
                (68 * resources.displayMetrics.density).toInt(),
                (48 * resources.displayMetrics.density).toInt(),
                Gravity.CENTER
            )
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(
                (8 * resources.displayMetrics.density).toInt(),
                0,
                (8 * resources.displayMetrics.density).toInt(),
                0
            )
            contentDescription = getString(R.string.language)
            isClickable = true
            isFocusable = true
            setOnClickListener { showLanguageDialog() }
        }
        item.actionView = languageMark
        item.title = getString(R.string.language)
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
            ticker.isSelected = false
            ticker.post {
                ticker.isSelected = true
                ticker.requestLayout()
            }
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
