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
import com.bustime.app.data.repository.AppRepository
import com.bustime.app.ui.details.RouteDetailActivity
import com.bustime.app.ui.home.BusAdapter
import com.bustime.app.ui.home.BusItem
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

        findViewById<Button>(R.id.btnBackSearch).setOnClickListener { finish() }

        val mode = intent.getStringExtra("MODE")
        if (mode == null) {
            finish()
            return
        }

        val tvTitle = findViewById<TextView>(R.id.tvSearchTitle)
        val tvEmpty = findViewById<TextView>(R.id.tvSearchEmpty)
        val recycler = findViewById<RecyclerView>(R.id.recyclerViewResults)
        recycler.layoutManager = LinearLayoutManager(this)
        val adapter = BusAdapter { item ->
            startActivity(Intent(this, RouteDetailActivity::class.java).putExtra("ROUTE_ID", item.routeId))
        }
        recycler.adapter = adapter

        val repo = AppRepository.getInstance(this)

        lifecycleScope.launch {
            val lang = LanguageHelper.current()
            val items: List<BusItem>
            val emptyRes: Int

            if (mode == "stand") {
                val standId = intent.getStringExtra("STAND_ID")
                val stand = if (standId == null) null else repo.getPlacesById(setOf(standId))[standId]
                if (standId == null || stand == null) {
                    finish()
                    return@launch
                }
                val results = repo.busesAtStand(standId)
                val names = repo.getPlacesById(results.flatMap { listOf(it.routeFromId, it.routeToId) }.toSet())
                fun name(id: String) = names[id]?.displayName(lang) ?: id

                tvTitle.text = getString(R.string.results_stand, stand.displayName(lang))
                emptyRes = R.string.no_stand_buses
                items = results.map { r ->
                    BusItem(
                        routeId = r.routeId,
                        time = TimeFormat.format(r.atTime),
                        title = "${name(r.routeFromId)} → ${name(r.routeToId)}",
                        extra = r.serviceName,
                        contact = r.contact
                    )
                }
            } else {
                val fromId = intent.getStringExtra("FROM_ID")
                val toId = intent.getStringExtra("TO_ID")
                if (fromId == null || toId == null) {
                    finish()
                    return@launch
                }
                val results = repo.searchBusRoutes(fromId, toId)
                val names = repo.getPlacesById(
                    results.flatMap { listOf(it.routeFromId, it.routeToId) }.toSet() + fromId + toId
                )
                fun name(id: String) = names[id]?.displayName(lang) ?: id

                tvTitle.text = getString(R.string.results_routes, name(fromId), name(toId))
                emptyRes = R.string.no_buses
                items = results.map { r ->
                    val arrival = r.toTime?.let { getString(R.string.arrives_at, TimeFormat.format(it)) }
                    BusItem(
                        routeId = r.routeId,
                        time = TimeFormat.format(r.fromTime),
                        title = "${name(r.routeFromId)} → ${name(r.routeToId)}",
                        extra = listOfNotNull(arrival, r.serviceName).joinToString(" • ").ifEmpty { null },
                        contact = r.contact
                    )
                }
            }

            adapter.submit(items)
            tvEmpty.setText(emptyRes)
            tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
