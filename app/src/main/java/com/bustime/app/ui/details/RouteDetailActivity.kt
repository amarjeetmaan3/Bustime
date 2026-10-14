package com.bustime.app.ui.details

import android.os.Bundle
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bustime.app.R
import com.bustime.app.data.repository.AppRepository
import com.bustime.app.utils.ContactActions
import com.bustime.app.utils.LanguageHelper
import com.bustime.app.utils.AnalyticsHelper
import com.bustime.app.utils.TimeFlow
import com.bustime.app.utils.TimeFormat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * एक रूट की पूरी जानकारी: सर्विस, संपर्क, जानकारी और सारे पड़ाव समय के साथ।
 * अगर बस आधी रात पार करती है तो अगले दिन के पड़ावों पर तारीख अपने-आप दिखती है
 * (कोई अलग "Long Route" चेकबॉक्स नहीं; समय के क्रम से तय होता है)।
 */
class RouteDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_detail)
        intent.getStringExtra("ROUTE_ID")?.let { AnalyticsHelper.logEvent(this, "route_detail_view", "bus", org.json.JSONObject().put("route_id", it)) }

        findViewById<Button>(R.id.btnBackDetail).visibility = View.GONE
        // Keep route correction fixed at the bottom while the stops list scrolls.
        val scroll = (findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as? ScrollView)
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
        correctionButton.setOnClickListener {
            val id = intent.getStringExtra("ROUTE_ID") ?: return@setOnClickListener
            startActivity(android.content.Intent(this, com.bustime.app.ui.community.CommunityRequestActivity::class.java)
                .putExtra("mode", "correction")
                .putExtra("route_id", id))
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

            findViewById<TextView>(R.id.tvRouteTitle).text = "${name(route.fromId)} → ${name(route.toId)}"
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

            setOptional(R.id.tvRouteService, route.serviceName ?: route.routeName)
            setOptional(R.id.tvRouteInfo, route.info)

            val btnCall = findViewById<Button>(R.id.btnRouteCall)
            val contact = route.contact
            if (contact.isNullOrEmpty()) {
                btnCall.visibility = View.GONE
            } else {
                btnCall.visibility = View.VISIBLE
                btnCall.setOnClickListener { ContactActions.dial(this@RouteDetailActivity, contact) }
            }

            // आधी रात पार होने पर किस पड़ाव का दिन अगला है (आज की तारीख से गिनकर)
            val flow = TimeFlow.resolve(stops.map { it.time })
            val days = if (flow.badIndex >= 0) stops.map { null } else flow.days
            val hasNextDay = days.any { (it ?: 0) > 0 }
            val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())

            val container = findViewById<LinearLayout>(R.id.stopsContainer)
            container.removeAllViews()
            val inflater = LayoutInflater.from(this@RouteDetailActivity)
            for ((i, s) in stops.withIndex()) {
                val row = inflater.inflate(R.layout.item_stop, container, false)
                val tvStopTime = row.findViewById<TextView>(R.id.tvStopTime)
                val tvStopName = row.findViewById<TextView>(R.id.tvStopName)
                tvStopTime.text = s.time?.takeIf { it.isNotBlank() }?.let { TimeFormat.format(it) }.orEmpty()
                tvStopName.text = name(s.placeId)

                // In route-search results, highlight the exact searched stops, even when intermediate.
                val highlightColor = when (s.placeId) {
                    intent.getStringExtra("SEARCH_FROM_ID") -> getColor(R.color.stopFromHighlight)
                    intent.getStringExtra("SEARCH_TO_ID") -> getColor(R.color.stopToHighlight)
                    else -> null
                }
                if (highlightColor != null) {
                    tvStopTime.setTextColor(highlightColor)
                    tvStopName.setTextColor(highlightColor)
                    row.findViewById<View>(R.id.stopDot).backgroundTintList =
                        ColorStateList.valueOf(highlightColor)
                }

                if (i == 0) {
                    row.findViewById<TextView>(R.id.tvStopTime).textSize = 18f
                    row.findViewById<TextView>(R.id.tvStopName).textSize = 18f
                }
                row.findViewById<View>(R.id.lineTop).visibility = if (i == 0) View.INVISIBLE else View.VISIBLE
                row.findViewById<View>(R.id.lineBottom).visibility = if (i == stops.size - 1) View.INVISIBLE else View.VISIBLE
                val day = days[i]
                if (hasNextDay && day != null) {
                    val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, day) }
                    val tv = row.findViewById<TextView>(R.id.tvStopDay)
                    tv.text = if (day > 0) getString(R.string.next_day_format, dateFormat.format(cal.time)) else dateFormat.format(cal.time)
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
