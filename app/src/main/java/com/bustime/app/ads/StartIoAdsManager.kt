package com.bustime.app.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.nativead.NativeAdDetails
import com.startapp.sdk.ads.nativead.NativeAdPreferences
import com.startapp.sdk.ads.nativead.StartAppNativeAd
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.bustime.app.BuildConfig
import com.bustime.app.utils.Constants
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class StartIoAdConfig(
    val appId: String = "",
    val bannerEnabled: Boolean = false,
    val mainBannerEnabled: Boolean = false,
    val bookBusBannerEnabled: Boolean = false,
    val nativeEnabled: Boolean = false,
    val resultsNativeEnabled: Boolean = false,
    val fullscreenEnabled: Boolean = false,
    val busEvery: Int = 4,
    val fullscreenEvery: Int = 5
)

/** Start.io network ads. The separate CustomPopupManager campaign system is intentionally untouched. */
object StartIoAdsManager {
    private val main = Handler(Looper.getMainLooper())
    private val initLock = Any()
    @Volatile private var initializedAppId = ""
    @Volatile private var initializingAppId = ""
    private val initCallbacks = mutableListOf<() -> Unit>()

    fun refresh(context: Context, callback: (StartIoAdConfig) -> Unit) {
        Thread {
            val config = fetchConfig(context.applicationContext)
            if (config.appId.isBlank()) {
                main.post { callback(config) }
            } else {
                initialize(context.applicationContext, config.appId) {
                    main.post { callback(config) }
                }
            }
        }.start()
    }

    private fun fetchConfig(context: Context): StartIoAdConfig {
        return try {
            val endpoint = Constants.SUPABASE_URL.trimEnd('/') +
                "/rest/v1/ad_settings?select=config&id=eq.1"
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 7000
                setRequestProperty("apikey", Constants.SUPABASE_ANON_KEY)
                if (Constants.SUPABASE_ANON_KEY.startsWith("eyJ")) {
                    setRequestProperty("Authorization", "Bearer ${Constants.SUPABASE_ANON_KEY}")
                }
            }
            try {
                if (connection.responseCode !in 200..299) return StartIoAdConfig()
                val rows = org.json.JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
                val root = rows.optJSONObject(0)?.optJSONObject("config") ?: return StartIoAdConfig()
                val cfg = root.optJSONObject("startio") ?: JSONObject()
                StartIoAdConfig(
                    appId = cfg.optString("app_id").trim(),
                    bannerEnabled = cfg.optBoolean("banner_enabled", false),
                    mainBannerEnabled = cfg.optBoolean("main_banner_enabled", false),
                    bookBusBannerEnabled = cfg.optBoolean("book_bus_banner_enabled", false),
                    nativeEnabled = cfg.optBoolean("native_enabled", false),
                    resultsNativeEnabled = cfg.optBoolean("results_native_enabled", false),
                    fullscreenEnabled = cfg.optBoolean("fullscreen_enabled", false),
                    busEvery = cfg.optInt("bus_every", 4).coerceIn(1, 20),
                    fullscreenEvery = cfg.optInt("fullscreen_every", 5).coerceIn(2, 20)
                )
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            StartIoAdConfig()
        }
    }

    private fun initialize(context: Context, appId: String, callback: () -> Unit) {
        synchronized(initLock) {
            if (initializedAppId == appId) {
                callback()
                return
            }
            initCallbacks.add(callback)
            if (initializingAppId == appId) return
            initializingAppId = appId
        }
        try {
            StartAppSDK.setTestAdsEnabled(BuildConfig.DEBUG)
            StartAppSDK.initParams(context, appId)
                .setReturnAdsEnabled(false)
                .setCallback {
                    val callbacks: List<() -> Unit>
                    synchronized(initLock) {
                        initializedAppId = appId
                        initializingAppId = ""
                        callbacks = initCallbacks.toList()
                        initCallbacks.clear()
                    }
                    callbacks.forEach { it() }
                }
                .init()
        } catch (_: Exception) {
            val callbacks: List<() -> Unit>
            synchronized(initLock) {
                initializingAppId = ""
                callbacks = initCallbacks.toList()
                initCallbacks.clear()
            }
            callbacks.forEach { it() }
        }
    }

    fun attachBanner(context: Context, container: FrameLayout, placement: String) {
        container.removeAllViews()
        container.visibility = View.GONE
        refresh(context) { config ->
            val enabledForPlacement = when (placement) {
                "main" -> config.mainBannerEnabled
                "book_bus" -> config.bookBusBannerEnabled
                else -> false
            }
            if (!config.bannerEnabled || !enabledForPlacement || config.appId.isBlank() ||
                !container.isAttachedToWindow || (context is Activity && (context.isFinishing || context.isDestroyed))) {
                container.visibility = View.GONE
                return@refresh
            }
            try {
                val banner = Banner(context)
                container.addView(
                    banner,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    )
                )
                container.visibility = View.VISIBLE
            } catch (_: Exception) {
                container.removeAllViews()
                container.visibility = View.GONE
            }
        }
    }

    fun loadNativeAd(context: Context, resultCount: Int, callback: (NativeAdDetails?, StartIoAdConfig) -> Unit) {
        refresh(context) { config ->
            if (!config.nativeEnabled || !config.resultsNativeEnabled ||
                config.appId.isBlank() || resultCount <= config.busEvery) {
                callback(null, config)
                return@refresh
            }
            try {
                val preferences = NativeAdPreferences().apply { setAutoBitmapDownload(true) }
                val nativeAd = StartAppNativeAd(context.applicationContext)
                nativeAd.setPreferences(preferences)
                nativeAd.loadAd(object : AdEventListener {
                    override fun onReceiveAd(ad: Ad) {
                        val details = nativeAd.nativeAds?.firstOrNull()
                        callback(details, config)
                    }

                    override fun onFailedToReceiveAd(ad: Ad?) {
                        callback(null, config)
                    }
                })
            } catch (_: Exception) {
                callback(null, config)
            }
        }
    }

    /** Shows an interstitial only at the remotely configured frequency; navigation always continues on failure. */
    fun maybeShowFullscreen(activity: Activity, onContinue: () -> Unit) {
        refresh(activity) { config ->
            if (!config.fullscreenEnabled || config.appId.isBlank() ||
                activity.isFinishing || activity.isDestroyed) {
                onContinue()
                return@refresh
            }
            val prefs = activity.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val count = prefs.getInt("startio_route_open_count", 0) + 1
            prefs.edit().putInt("startio_route_open_count", count).apply()
            if (count % config.fullscreenEvery != 0) {
                onContinue()
                return@refresh
            }
            var continued = false
            fun continueOnce() {
                if (!continued) {
                    continued = true
                    if (!activity.isFinishing && !activity.isDestroyed) onContinue()
                }
            }
            try {
                val interstitial = StartAppAd(activity)
                interstitial.loadAd(object : AdEventListener {
                    override fun onReceiveAd(ad: Ad) {
                        interstitial.setAdDisplayListener(object : AdDisplayListener {
                            override fun adHidden(ad: Ad) = continueOnce()
                            override fun adDisplayed(ad: Ad) = Unit
                            override fun adClicked(ad: Ad) = Unit
                            override fun adNotDisplayed(ad: Ad) = continueOnce()
                        })
                        if (!interstitial.showAd()) continueOnce()
                    }

                    override fun onFailedToReceiveAd(ad: Ad?) = continueOnce()
                })
            } catch (_: Exception) {
                continueOnce()
            }
        }
    }

    fun buildNativeAdView(context: Context, ad: NativeAdDetails): View {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(0xFFF2F5F9.toInt())
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        card.addView(TextView(context).apply {
            text = "Ad · Sponsored"
            textSize = 12f
            setTextColor(0xFF526174.toInt())
        })
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        ad.imageBitmap?.let { bitmap ->
            row.addView(ImageView(context).apply {
                setImageBitmap(bitmap)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }, LinearLayout.LayoutParams(dp(88), dp(88)).apply { marginEnd = dp(12) })
        }
        val details = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        details.addView(TextView(context).apply {
            text = ad.title
            textSize = 16f
            setTextColor(0xFF182230.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        details.addView(TextView(context).apply {
            text = ad.description
            textSize = 13f
            maxLines = 3
            setTextColor(0xFF526174.toInt())
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
        row.addView(details, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(row)
        if (ad.callToAction.isNotBlank()) {
            card.addView(Button(context).apply {
                text = ad.callToAction
                isAllCaps = false
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.END
            })
        }
        ad.registerViewForInteraction(card)
        return card
    }
}
