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
            findViewById<TextView>(R.id.tvRouteTime).text =
                getString(R.string.departs_at, TimeFormat.format(route.time))

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
            for (s in stops) {
                val tv = TextView(this@RouteDetailActivity).apply {
                    text = "${TimeFormat.format(s.time)}     ${name(s.placeId)}"
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
