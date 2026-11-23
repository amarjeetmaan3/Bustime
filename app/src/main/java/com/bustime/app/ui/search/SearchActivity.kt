package com.bustime.app.ui.search

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bustime.app.R
import com.bustime.app.ads.AdMobAdsManager
import com.bustime.app.data.repository.AppRepository
import com.bustime.app.ui.common.TransitDurationFormatter
import com.bustime.app.ui.details.RouteDetailActivity
import com.bustime.app.ui.home.BusAdapter
import com.bustime.app.ui.home.BusItem
import com.bustime.app.utils.AnalyticsHelper
import com.bustime.app.utils.LanguageHelper
import com.bustime.app.utils.TimeFormat
import kotlinx.coroutines.launch

/**
 * सर्च का नतीजा अलग पेज पर। दो तरह से खुलता है:
 *  MODE = "route": FROM_ID और TO_ID के साथ (From -> To)
 *  MODE = "stand": STAND_ID के साथ (बस स्टैंड पर आने-जाने वाली बसें)
 */
class SearchActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        findViewById<Button>(R.id.btnBackSearch).visibility = View.GONE
        findViewById<View>(R.id.btnNavBackSearch)?.setOnClickListener { finish() }
        findViewById<View>(R.id.btnRefreshSearch)?.setOnClickListener { btn ->
            btn.animate().rotationBy(360f).setDuration(350).withEndAction { recreate() }.start()
        }

        val mode = intent.getStringExtra("MODE")
        if (mode == null) {
            finish()
            return
        }

        val tvTitle = findViewById<TextView>(R.id.tvSearchTitle)
        val tvSubtitle = findViewById<TextView>(R.id.tvSearchSubtitle)
        val tvEmpty = findViewById<TextView>(R.id.tvSearchEmpty)
        val recycler = findViewById<RecyclerView>(R.id.recyclerViewResults)
        recycler.layoutManager = LinearLayoutManager(this)
        val adapter = BusAdapter { item ->
            val detailIntent = Intent(this, RouteDetailActivity::class.java)
                .putExtra("ROUTE_ID", item.routeId)
            // Route search context lets the detail screen highlight the exact selected From/To stops.
            if (mode == "route") {
                intent.getStringExtra("FROM_ID")?.let { detailIntent.putExtra("SEARCH_FROM_ID", it) }
                intent.getStringExtra("TO_ID")?.let { detailIntent.putExtra("SEARCH_TO_ID", it) }
            }
            AdMobAdsManager.maybeShowFullscreen(this) { startActivity(detailIntent) }
        }
        recycler.adapter = adapter

        val repo = AppRepository.getInstance(this)

        lifecycleScope.launch {
            val lang = LanguageHelper.current()
            val stopCounts = repo.getAllRouteStops().groupingBy { it.routeId }.eachCount()
            val items: List<BusItem>
            val emptyRes: Int

            if (mode == "stand") {
                val standId = intent.getStringExtra("STAND_ID")
                val stand = if (standId == null) null else repo.getPlacesById(setOf(standId))[standId]
                if (standId == null || stand == null) {
                    finish()
                    return@launch
                }
                AnalyticsHelper.logStandSearch(this@SearchActivity, standId)
                val results = repo.busesAtStand(standId)
                val names = repo.getPlacesById(results.flatMap { listOf(it.routeFromId, it.routeToId) }.toSet())
                fun name(id: String) = names[id]?.displayName(lang) ?: id

                tvTitle.text = getString(R.string.passing_buses)
                tvSubtitle.text = "${stand.displayName(lang)} • ${getString(R.string.buses_found_count, results.size)}"
                tvSubtitle.visibility = View.VISIBLE
                emptyRes = R.string.no_stand_buses

                items = results.map { r ->
                    val routeEndpoints = "${name(r.routeFromId)} → ${name(r.routeToId)}"
                    val primaryTitle = r.serviceName?.takeIf { it.isNotBlank() } ?: routeEndpoints
                    val subtitle = if (primaryTitle == routeEndpoints) null else routeEndpoints
                    val stopsLabel = (stopCounts[r.routeId] ?: 0).takeIf { it > 0 }
                        ?.let { getString(R.string.stops_count_label, it) }
                    val pillText = listOfNotNull(r.info?.takeIf { it.isNotBlank() }, stopsLabel)
                        .joinToString(" • ").ifEmpty { null }
                    BusItem(
                        routeId = r.routeId,
                        time = TimeFormat.format(r.atTime),
                        title = primaryTitle,
                        extra = r.info?.takeIf { it.isNotBlank() },
                        contact = r.contact,
                        isStandMode = true,
                        routeSubtitle = subtitle,
                        metaPill = pillText
                    )
                }
            } else {
                val fromId = intent.getStringExtra("FROM_ID")
                val toId = intent.getStringExtra("TO_ID")
                if (fromId == null || toId == null) {
                    finish()
                    return@launch
                }
                AnalyticsHelper.logSearchEvent(this@SearchActivity, fromId, toId, "bus")
                val results = repo.searchBusRoutes(fromId, toId)
                val names = repo.getPlacesById(
                    results.flatMap { listOf(it.routeFromId, it.routeToId) }.toSet() + fromId + toId
                )
                fun name(id: String) = names[id]?.displayName(lang) ?: id

                val fromLabel = name(fromId)
                val toLabel = name(toId)
                tvTitle.text = getString(R.string.available_buses)
                tvSubtitle.text = "$fromLabel → $toLabel • ${getString(R.string.buses_found_count, results.size)}"
                tvSubtitle.visibility = View.VISIBLE
                emptyRes = R.string.no_buses

                items = results.map { r ->
                    val routeEndpoints = "${name(r.routeFromId)} → ${name(r.routeToId)}"
                    val primaryTitle = r.serviceName?.takeIf { it.isNotBlank() } ?: routeEndpoints
                    val stopsLabel = (stopCounts[r.routeId] ?: 0).takeIf { it > 0 }
                        ?.let { getString(R.string.stops_count_label, it) }
                    val footerInfo = listOfNotNull(
                        routeEndpoints.takeIf { it != primaryTitle },
                        r.info?.takeIf { it.isNotBlank() },
                        stopsLabel
                    ).joinToString(" • ").ifEmpty { null }
                    BusItem(
                        routeId = r.routeId,
                        time = TimeFormat.format(r.fromTime),
                        title = primaryTitle,
                        extra = footerInfo,
                        contact = r.contact,
                        arrivalTime = r.toTime?.let { TimeFormat.format(it) },
                        durationLabel = TransitDurationFormatter.formatBetween(r.fromTime, r.toTime),
                        fromStopName = fromLabel,
                        toStopName = toLabel
                    )
                }
            }

            adapter.submit(items)
            recycler.scheduleLayoutAnimation()
            tvEmpty.setText(emptyRes)
            tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            if (items.isNotEmpty()) {
                AdMobAdsManager.loadNativeAds(this@SearchActivity, items.size) { nativeAds, _ ->
                    if (!isFinishing && !isDestroyed && nativeAds.isNotEmpty()) {
                        adapter.submit(items, nativeAds)
                    }
                }
            }
        }
    }
}
