package com.bustime.app.ui.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bustime.app.R
import com.bustime.app.data.repository.AppRepository
import com.bustime.app.ui.common.GlowingStopNodeView
import com.bustime.app.ui.common.TransitDurationFormatter
import com.bustime.app.ui.common.applyBouncyPress
import com.bustime.app.utils.AnalyticsHelper
import com.bustime.app.utils.ContactActions
import com.bustime.app.utils.LanguageHelper
import com.bustime.app.utils.TimeFlow
import com.bustime.app.utils.TimeFormat
import kotlinx.coroutines.launch

/**
 * एक रूट की पूरी जानकारी: सर्विस, संपर्क, जानकारी और सारे पड़ाव समय के साथ।
 * अगर बस आधी रात पार करती है तो अगले दिन के पड़ावों पर +1 Day बैज और ओवरनाइट बैनर अपने-आप दिखता है
 * (कोई अलग "Long Route" चेकबॉक्स नहीं; समय के क्रम से तय होता है)।
 */
class RouteDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_detail)
        intent.getStringExtra("ROUTE_ID")?.let {
            AnalyticsHelper.logEvent(this, "route_detail_view", "bus", org.json.JSONObject().put("route_id", it))
        }

        findViewById<Button>(R.id.btnBackDetail).visibility = View.GONE
        findViewById<View>(R.id.btnNavBackDetail)?.setOnClickListener { finish() }

        // Keep route correction fixed at the bottom while the stops list scrolls.
        val scroll = (findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as? ScrollView)
        val correctionButton = findViewById<Button>(R.id.btnRouteCorrection)
        if (scroll != null && scroll.childCount > 0 && correctionButton.parent is ViewGroup) {
            val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            (scroll.parent as? ViewGroup)?.removeView(scroll)
            (correctionButton.parent as ViewGroup).removeView(correctionButton)
            page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            correctionButton.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            correctionButton.setPadding(16, 12, 16, 12)
            page.addView(correctionButton)
            setContentView(page)
        }
        correctionButton.applyBouncyPress()
        correctionButton.setOnClickListener {
            val id = intent.getStringExtra("ROUTE_ID") ?: return@setOnClickListener
            startActivity(
                android.content.Intent(this, com.bustime.app.ui.community.CommunityRequestActivity::class.java)
                    .putExtra("mode", "correction")
                    .putExtra("route_id", id)
            )
        }

        val routeId = intent.getStringExtra("ROUTE_ID")
        if (routeId == null) {
            finish()
            return
        }
        val repo = AppRepository.getInstance(this)

        lifecycleScope.launch {
            val route = repo.getRoute(routeId)
            if (route == null) {
                finish()
                return@launch
            }
            val stops = repo.getRouteStops(routeId)
            val ids = stops.map { it.placeId }.toSet() + route.fromId + route.toId
            val names = repo.getPlacesById(ids)
            val lang = LanguageHelper.current()
            fun name(id: String) = names[id]?.displayName(lang) ?: id

            val routeEndpoints = "${name(route.fromId)} → ${name(route.toId)}"
            val serviceTitle = (route.serviceName ?: route.routeName)?.takeIf { it.isNotBlank() }

            // Top bar 2-line header: Bus Name + Endpoints
            findViewById<TextView>(R.id.tvDetailBarTitle)?.text = serviceTitle ?: routeEndpoints
            findViewById<TextView>(R.id.tvDetailBarSubtitle)?.apply {
                text = routeEndpoints
                visibility = if (serviceTitle != null) View.VISIBLE else View.GONE
            }

            // Floating Hero Card Title & Optional Subtitle
            findViewById<TextView>(R.id.tvRouteTitle).text = serviceTitle ?: routeEndpoints
            setOptional(R.id.tvRouteService, if (serviceTitle != null) routeEndpoints else null)
            setOptional(R.id.tvRouteInfo, route.info)

            val firstStopTime = stops.firstOrNull()?.time?.takeIf { it.isNotBlank() }
            val lastStopTime = stops.lastOrNull()?.time?.takeIf { it.isNotBlank() }
            findViewById<TextView>(R.id.tvRouteTime).apply {
                // Show the departure banner only when BOTH endpoint stops have their own times.
                // Never substitute route.time or a searched intermediate stop's time.
                if (firstStopTime != null && lastStopTime != null) {
                    text = getString(R.string.departs_at, TimeFormat.format(firstStopTime))
                    visibility = View.VISIBLE
                } else {
                    text = ""
                    visibility = View.GONE
                }
            }

            findViewById<TextView>(R.id.tvRouteStopsPill)?.apply {
                if (stops.isNotEmpty()) {
                    text = getString(R.string.stops_count_label, stops.size)
                    visibility = View.VISIBLE
                } else {
                    visibility = View.GONE
                }
            }
            findViewById<TextView>(R.id.tvRouteDurationPill)?.apply {
                val dur = TransitDurationFormatter.formatBetween(firstStopTime, lastStopTime)
                if (dur != null) {
                    text = dur
                    visibility = View.VISIBLE
                } else {
                    visibility = View.GONE
                }
            }

            val btnCall = findViewById<Button>(R.id.btnRouteCall)
            val contact = route.contact
            if (contact.isNullOrEmpty()) {
                btnCall.visibility = View.GONE
            } else {
                btnCall.visibility = View.VISIBLE
                btnCall.applyBouncyPress()
                btnCall.setOnClickListener { ContactActions.dial(this@RouteDetailActivity, contact) }
            }

            // आधी रात पार होने पर किस पड़ाव का दिन अगला है (TimeFlow से तय)
            val flow = TimeFlow.resolve(stops.map { it.time })
            val days = if (flow.badIndex >= 0) stops.map { null } else flow.days
            val hasNextDay = days.any { (it ?: 0) > 0 }
            findViewById<View>(R.id.layoutOvernightBanner)?.visibility =
                if (hasNextDay) View.VISIBLE else View.GONE

            val container = findViewById<LinearLayout>(R.id.stopsContainer)
            container.removeAllViews()
            val inflater = LayoutInflater.from(this@RouteDetailActivity)
            val searchFromId = intent.getStringExtra("SEARCH_FROM_ID")
            val searchToId = intent.getStringExtra("SEARCH_TO_ID")

            for ((i, s) in stops.withIndex()) {
                val row = inflater.inflate(R.layout.item_stop, container, false)
                val tvStopTime = row.findViewById<TextView>(R.id.tvStopTime)
                val tvStopName = row.findViewById<TextView>(R.id.tvStopName)
                val tvStopAltNames = row.findViewById<TextView>(R.id.tvStopAltNames)
                val stopNode = row.findViewById<GlowingStopNodeView>(R.id.stopNodeView)

                val rawTime = s.time?.takeIf { it.isNotBlank() }
                if (rawTime != null) {
                    tvStopTime.text = TimeFormat.format(rawTime)
                    tvStopTime.visibility = View.VISIBLE
                } else {
                    tvStopTime.text = "—"
                    tvStopTime.setTextColor(getColor(R.color.textMuted))
                }

                val primaryName = name(s.placeId)
                tvStopName.text = primaryName

                // Show other language names (e.g. हिंदी • ਪੰਜਾਬੀ) directly under the stop name
                val altNames = names[s.placeId]?.names
                    ?.filterKeys { it != lang }
                    ?.values
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() && !it.equals(primaryName, ignoreCase = true) }
                    ?.distinct()
                    ?.joinToString(" • ")
                if (!altNames.isNullOrBlank()) {
                    tvStopAltNames.text = altNames
                    tvStopAltNames.visibility = View.VISIBLE
                } else {
                    tvStopAltNames.visibility = View.GONE
                }

                // Glowing node coloring: Emerald start -> Sky Blue middle -> Saffron end (plus search highlights)
                val highlightColor = when (s.placeId) {
                    searchFromId -> getColor(R.color.stopFromHighlight)
                    searchToId -> getColor(R.color.stopToHighlight)
                    else -> null
                }
                val isEndpoint = (i == 0 || i == stops.lastIndex || highlightColor != null)
                val nodeColor = when {
                    highlightColor != null -> highlightColor
                    i == 0 -> getColor(R.color.colorTertiary)
                    i == stops.lastIndex -> getColor(R.color.colorAccent)
                    else -> getColor(R.color.stopNodeMid)
                }
                stopNode.configure(
                    color = nodeColor,
                    hasTop = i > 0,
                    hasBottom = i < stops.lastIndex,
                    endpoint = isEndpoint
                )

                if (highlightColor != null) {
                    tvStopTime.setTextColor(highlightColor)
                    tvStopName.setTextColor(highlightColor)
                }
                if (i == 0 || i == stops.lastIndex) {
                    tvStopName.textSize = 16.5f
                }

                val day = days[i]
                if (hasNextDay && day != null && day > 0) {
                    val tv = row.findViewById<TextView>(R.id.tvStopDay)
                    tv.text = getString(R.string.plus_day_short, day)
                    tv.visibility = View.VISIBLE
                }
                container.addView(row)
            }
        }
    }

    private fun setOptional(viewId: Int, text: String?) {
        val tv = findViewById<TextView>(viewId)
        if (text.isNullOrEmpty()) {
            tv.visibility = View.GONE
        } else {
            tv.visibility = View.VISIBLE
            tv.text = text
        }
    }
}
