package com.bustime.app.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bustime.app.R
import com.bustime.app.data.repository.AppRepository
import com.bustime.app.models.Place
import com.bustime.app.ui.details.RouteDetailActivity
import com.bustime.app.utils.LanguageHelper
import com.bustime.app.utils.TimeFormat
import kotlinx.coroutines.launch

class BusFragment : Fragment(R.layout.fragment_bus) {

    private lateinit var repo: AppRepository
    private lateinit var fromPicker: PlacePicker
    private lateinit var toPicker: PlacePicker
    private lateinit var standPicker: PlacePicker
    private lateinit var adapter: BusAdapter
    private lateinit var tvResultsTitle: TextView
    private lateinit var tvEmpty: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = AppRepository.getInstance(requireContext())
        val scope = viewLifecycleOwner.lifecycleScope

        tvResultsTitle = view.findViewById(R.id.tvResultsTitle)
        tvEmpty = view.findViewById(R.id.tvEmptyBus)

        val recycler = view.findViewById<RecyclerView>(R.id.recyclerViewBus)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        adapter = BusAdapter { item ->
            startActivity(
                Intent(requireContext(), RouteDetailActivity::class.java).putExtra("ROUTE_ID", item.routeId)
            )
        }
        recycler.adapter = adapter

        val etStand = view.findViewById<EditText>(R.id.etStand)
        fromPicker = PlacePicker(scope, repo, view.findViewById(R.id.etFrom), view.findViewById<LinearLayout>(R.id.suggestFrom), false)
        toPicker = PlacePicker(scope, repo, view.findViewById(R.id.etTo), view.findViewById<LinearLayout>(R.id.suggestTo), false)
        standPicker = PlacePicker(scope, repo, etStand, view.findViewById<LinearLayout>(R.id.suggestStand), true) { place ->
            showStandBuses(place)
        }

        view.findViewById<Button>(R.id.btnSearchRoute).setOnClickListener { searchRoutes() }

        // कीबोर्ड का Search बटन: पूरा स्टैंड नाम लिखा हो तो भी चलेगा
        etStand.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                scope.launch { standPicker.resolve() }
                true
            } else false
        }
    }

    // ---------- From -> To ----------
    private fun searchRoutes() {
        viewLifecycleOwner.lifecycleScope.launch {
            val from = fromPicker.resolve()
            val to = toPicker.resolve()
            val ctx = context ?: return@launch
            if (from == null || to == null) {
                Toast.makeText(ctx, R.string.select_places, Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (from.id == to.id) {
                Toast.makeText(ctx, R.string.same_place, Toast.LENGTH_SHORT).show()
                return@launch
            }

            val results = repo.searchBusRoutes(from.id, to.id)
            val names = repo.getPlacesById(results.flatMap { listOf(it.routeFromId, it.routeToId) }.toSet())
            val lang = LanguageHelper.current()
            fun name(id: String) = names[id]?.displayName(lang) ?: id

            val items = results.map { r ->
                val arrival = r.toTime?.let { getString(R.string.arrives_at, TimeFormat.format(it)) }
                BusItem(
                    routeId = r.routeId,
                    time = TimeFormat.format(r.fromTime),
                    title = "${name(r.routeFromId)} → ${name(r.routeToId)}",
                    extra = listOfNotNull(arrival, r.serviceName).joinToString(" • ").ifEmpty { null },
                    contact = r.contact
                )
            }
            showResults(
                getString(R.string.results_routes, from.displayName(lang), to.displayName(lang)),
                items, R.string.no_buses
            )
        }
    }

    // ---------- बस स्टैंड सर्च ----------
    private fun showStandBuses(place: Place) {
        viewLifecycleOwner.lifecycleScope.launch {
            val results = repo.busesAtStand(place.id)
            val names = repo.getPlacesById(results.flatMap { listOf(it.routeFromId, it.routeToId) }.toSet())
            val lang = LanguageHelper.current()
            fun name(id: String) = names[id]?.displayName(lang) ?: id

            val items = results.map { r ->
                BusItem(
                    routeId = r.routeId,
                    time = TimeFormat.format(r.atTime),
                    title = "${name(r.routeFromId)} → ${name(r.routeToId)}",
                    extra = r.serviceName,
                    contact = r.contact
                )
            }
            showResults(getString(R.string.results_stand, place.displayName(lang)), items, R.string.no_stand_buses)
        }
    }

    private fun showResults(title: String, items: List<BusItem>, emptyRes: Int) {
        adapter.submit(items)
        tvResultsTitle.text = title
        tvResultsTitle.visibility = View.VISIBLE
        tvEmpty.setText(emptyRes)
        tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }
}
