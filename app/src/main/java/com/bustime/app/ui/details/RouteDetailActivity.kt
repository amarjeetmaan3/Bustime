package com.bustime.app.ui.details

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bustime.app.R
import com.bustime.app.data.repository.AppRepository
import com.bustime.app.utils.ContactActions
import com.bustime.app.utils.LanguageHelper
import com.bustime.app.utils.TimeFormat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** एक रूट की पूरी जानकारी: सर्विस, संपर्क, जानकारी और सारे पड़ाव समय के साथ */
class RouteDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_detail)

        findViewById<Button>(R.id.btnBackDetail).setOnClickListener { finish() }

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
            findViewById<TextView>(R.id.tvRouteTime).text = getString(R.string.departs_at, TimeFormat.format(route.time)) +
                if (route.longRoute && !route.startDate.isNullOrBlank()) " · ${route.startDate}" else ""

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

            val container = findViewById<LinearLayout>(R.id.stopsContainer)
            container.removeAllViews()
            val pad = (8 * resources.displayMetrics.density).toInt()
            val serviceCalendar = Calendar.getInstance()
            var hasServiceDate = false
            if (!route.startDate.isNullOrBlank()) {
                try {
                    serviceCalendar.time = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(route.startDate) ?: Date()
                    hasServiceDate = true
                } catch (_: Exception) { hasServiceDate = false }
            }
            var previousTime: String? = null
            for (s in stops) {
                if (route.longRoute && s.time != null && previousTime != null && s.time < previousTime!!) {
                    serviceCalendar.add(Calendar.DAY_OF_MONTH, 1)
                }
                if (s.time != null) previousTime = s.time
                val dateLabel = if (route.longRoute && hasServiceDate) "${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(serviceCalendar.time)}  ·  " else ""
                val tv = TextView(this@RouteDetailActivity).apply {
                    text = "$dateLabel${TimeFormat.format(s.time)}     ${name(s.placeId)}"
                    textSize = 16f
                    setPadding(0, pad, 0, pad)
                }
                container.addView(tv)
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
