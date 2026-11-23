package com.bustime.app.ads

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
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
import com.bustime.app.utils.Constants
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Remotely controlled switches and ad unit IDs. Unit IDs left blank fall back to Google's official
 * test units, which is only allowed in debuggable builds (see [AdMobAdsManager.unitOrTest]).
 */
data class AdConfig(
    val bannerEnabled: Boolean = false,
    val mainBannerEnabled: Boolean = false,
    val bookBusBannerEnabled: Boolean = false,
    val nativeEnabled: Boolean = false,
    val resultsNativeEnabled: Boolean = false,
    val fullscreenEnabled: Boolean = false,
    val busEvery: Int = 4,
    val fullscreenEvery: Int = 5,
    val bannerUnitId: String = "",
    val nativeUnitId: String = "",
    val interstitialUnitId: String = ""
)

/** Google AdMob ads with the Google UMP consent flow. CustomPopupManager (own campaigns) is untouched. */
object AdMobAdsManager {
    // Google's documented sample ad units. They never serve real ads.
    private const val TEST_BANNER = "ca-app-pub-3940256099942544/9214589741"
    private const val TEST_NATIVE = "ca-app-pub-3940256099942544/2247696110"
    private const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"

    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val waiting = mutableListOf<(Boolean) -> Unit>()
    @Volatile private var ready = false
    @Volatile private var preparing = false
    private val mobileAdsStarted = AtomicBoolean(false)

    private fun isDebuggable(context: Context) =
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun unitOrTest(context: Context, configured: String, test: String): String? = when {
        configured.isNotBlank() -> configured
        isDebuggable(context) -> test
        else -> null
    }

    /** Runs the consent flow once, starts the SDK only when Google says ads may be requested. */
    private fun prepare(activity: Activity, callback: (Boolean) -> Unit) {
        synchronized(lock) {
            if (ready) { main.post { callback(true) }; return }
            waiting.add(callback)
            if (preparing) return
            preparing = true
        }
        fun finish(canRequest: Boolean) {
            if (canRequest && mobileAdsStarted.compareAndSet(false, true)) {
                MobileAds.initialize(activity.applicationContext) {}
            }
            val callbacks: List<(Boolean) -> Unit>
            synchronized(lock) {
                ready = canRequest
                preparing = false
                callbacks = waiting.toList()
                waiting.clear()
            }
            main.post { callbacks.forEach { it(canRequest) } }
        }
        try {
            val consent = UserMessagingPlatform.getConsentInformation(activity)
            consent.requestConsentInfoUpdate(
                activity,
                ConsentRequestParameters.Builder().build(),
                {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                        finish(consent.canRequestAds())
                    }
                },
                { finish(consent.canRequestAds()) }
            )
        } catch (_: Exception) {
            finish(false)
        }
    }

    private fun refresh(context: Context, callback: (AdConfig) -> Unit) {
        Thread {
            val config = fetchConfig()
            main.post { callback(config) }
        }.start()
    }

    private fun fetchConfig(): AdConfig {
        return try {
            val endpoint = Constants.SUPABASE_URL.trimEnd('/') + "/rest/v1/ad_settings?select=config&id=eq.1"
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 7000
                setRequestProperty("apikey", Constants.SUPABASE_ANON_KEY)
            }
            try {
                if (connection.responseCode !in 200..299) return AdConfig()
                val rows = org.json.JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
                val root = rows.optJSONObject(0)?.optJSONObject("config") ?: return AdConfig()
                val cfg = root.optJSONObject("admob") ?: root.optJSONObject("startio") ?: JSONObject()
                AdConfig(
                    bannerEnabled = cfg.optBoolean("banner_enabled", false),
                    mainBannerEnabled = cfg.optBoolean("main_banner_enabled", false),
                    bookBusBannerEnabled = cfg.optBoolean("book_bus_banner_enabled", false),
                    nativeEnabled = cfg.optBoolean("native_enabled", false),
                    resultsNativeEnabled = cfg.optBoolean("results_native_enabled", false),
                    fullscreenEnabled = cfg.optBoolean("fullscreen_enabled", false),
                    busEvery = cfg.optInt("bus_every", 4).coerceIn(1, 20),
                    fullscreenEvery = cfg.optInt("fullscreen_every", 5).coerceIn(2, 20),
                    bannerUnitId = cfg.optString("banner_unit_id").trim(),
                    nativeUnitId = cfg.optString("native_unit_id").trim(),
                    interstitialUnitId = cfg.optString("interstitial_unit_id").trim()
                )
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            AdConfig()
        }
    }

    private fun gone(context: Context) = context is Activity && (context.isFinishing || context.isDestroyed)

    fun attachBanner(context: Context, container: FrameLayout, placement: String) {
        container.removeAllViews()
        container.visibility = View.GONE
        if (context !is Activity) return
        refresh(context) { config ->
            val enabledForPlacement = when (placement) {
                "main" -> config.mainBannerEnabled
                "book_bus" -> config.bookBusBannerEnabled
                else -> false
            }
            val unit = unitOrTest(context, config.bannerUnitId, TEST_BANNER)
            if (!config.bannerEnabled || !enabledForPlacement || unit == null || gone(context)) return@refresh
            prepare(context) { canRequest ->
                if (!canRequest || gone(context)) return@prepare
                try {
                    val density = context.resources.displayMetrics.density
                    val widthDp = (context.resources.displayMetrics.widthPixels / density).toInt()
                    val banner = AdView(context).apply {
                        setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
                        adUnitId = unit
                        adListener = object : AdListener() {
                            override fun onAdLoaded() { container.visibility = View.VISIBLE }
                            override fun onAdFailedToLoad(error: LoadAdError) { container.visibility = View.GONE }
                        }
                    }
                    container.addView(
                        banner,
                        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
                    )
                    banner.loadAd(AdRequest.Builder().build())
                } catch (_: Exception) {
                    container.removeAllViews()
                    container.visibility = View.GONE
                }
            }
        }
    }

    /** Loads one native ad for each configured block of results (after 4, 8, 12...), at most 5. */
    fun loadNativeAds(
        context: Context,
        resultCount: Int,
        callback: (List<Pair<Int, NativeAd>>, AdConfig) -> Unit
    ) {
        if (context !is Activity) { callback(emptyList(), AdConfig()); return }
        refresh(context) { config ->
            val unit = unitOrTest(context, config.nativeUnitId, TEST_NATIVE)
            if (!config.nativeEnabled || !config.resultsNativeEnabled || unit == null ||
                resultCount < config.busEvery || gone(context)) {
                callback(emptyList(), config)
                return@refresh
            }
            prepare(context) { canRequest ->
                if (!canRequest || gone(context)) {
                    callback(emptyList(), config)
                    return@prepare
                }
                val adCount = (resultCount / config.busEvery).coerceIn(1, 5)
                val loaded = mutableListOf<Pair<Int, NativeAd>>()
                var completed = 0
                fun finishOne() {
                    completed++
                    if (completed == adCount) callback(loaded.sortedBy { it.first }.toList(), config)
                }
                repeat(adCount) { index ->
                    try {
                        AdLoader.Builder(context, unit)
                            .forNativeAd { ad ->
                                if (gone(context)) ad.destroy() else loaded.add((index + 1) * config.busEvery to ad)
                                finishOne()
                            }
                            .withAdListener(object : AdListener() {
                                override fun onAdFailedToLoad(error: LoadAdError) = finishOne()
                            })
                            .build()
                            .loadAd(AdRequest.Builder().build())
                    } catch (_: Exception) {
                        finishOne()
                    }
                }
            }
        }
    }

    /** Shows an interstitial only at the configured frequency; navigation always continues on failure. */
    fun maybeShowFullscreen(activity: Activity, onContinue: () -> Unit) {
        refresh(activity) { config ->
            val unit = unitOrTest(activity, config.interstitialUnitId, TEST_INTERSTITIAL)
            if (!config.fullscreenEnabled || unit == null || gone(activity)) {
                onContinue()
                return@refresh
            }
            val prefs = activity.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val count = prefs.getInt("admob_route_open_count", 0) + 1
            prefs.edit().putInt("admob_route_open_count", count).apply()
            if (count % config.fullscreenEvery != 0) {
                onContinue()
                return@refresh
            }
            var continued = false
            fun continueOnce() {
                if (!continued) {
                    continued = true
                    if (!gone(activity)) onContinue()
                }
            }
            prepare(activity) { canRequest ->
                if (!canRequest || gone(activity)) {
                    continueOnce()
                    return@prepare
                }
                try {
                    InterstitialAd.load(activity, unit, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
                        override fun onAdLoaded(ad: InterstitialAd) {
                            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                                override fun onAdDismissedFullScreenContent() = continueOnce()
                                override fun onAdFailedToShowFullScreenContent(error: AdError) = continueOnce()
                            }
                            if (gone(activity)) continueOnce() else ad.show(activity)
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) = continueOnce()
                    })
                } catch (_: Exception) {
                    continueOnce()
                }
            }
        }
    }

    fun buildNativeAdView(context: Context, ad: NativeAd): View {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val adView = NativeAdView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundColor(0xFFF2F5F9.toInt())
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(TextView(context).apply {
            text = "Ad"
            textSize = 12f
            setTextColor(0xFF526174.toInt())
            setTypeface(typeface, Typeface.BOLD)
        })
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val iconView = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        ad.icon?.drawable?.let { drawable ->
            iconView.setImageDrawable(drawable)
            row.addView(iconView, LinearLayout.LayoutParams(dp(64), dp(64)).apply { marginEnd = dp(12) })
            adView.iconView = iconView
        }
        val details = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val headline = TextView(context).apply {
            text = ad.headline
            textSize = 16f
            setTextColor(0xFF182230.toInt())
            setTypeface(typeface, Typeface.BOLD)
        }
        details.addView(headline)
        adView.headlineView = headline
        ad.body?.takeIf { it.isNotBlank() }?.let { bodyText ->
            val body = TextView(context).apply {
                text = bodyText
                textSize = 13f
                maxLines = 3
                setTextColor(0xFF526174.toInt())
            }
            details.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
            adView.bodyView = body
        }
        row.addView(details, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        column.addView(row)
        ad.callToAction?.takeIf { it.isNotBlank() }?.let { cta ->
            val button = Button(context).apply {
                text = cta
                isAllCaps = false
            }
            column.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.END })
            adView.callToActionView = button
        }
        adView.addView(column)
        adView.setNativeAd(ad)
        return adView
    }
}
