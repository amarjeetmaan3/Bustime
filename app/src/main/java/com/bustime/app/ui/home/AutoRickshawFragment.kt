package com.bustime.app.ui.home

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.bustime.app.R
import com.bustime.app.data.repository.AppRepository
import kotlinx.coroutines.launch

/** सारे ऑटो एक लिस्ट में; ऊपर All | EV | Oil */
class AutoRickshawFragment : Fragment(R.layout.fragment_auto_rickshaw) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val repo = AppRepository.getInstance(requireContext())

        val recycler = view.findViewById<RecyclerView>(R.id.recyclerViewAutos)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        val adapter = DriverAdapter()
        recycler.adapter = adapter
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmptyAuto)
        view.findViewById<android.widget.Button>(R.id.btnAddAuto).setOnClickListener {
            startActivity(android.content.Intent(requireContext(), com.bustime.app.ui.community.CommunityRequestActivity::class.java).putExtra("mode", "vehicle").putExtra("vehicle_type", "auto"))
        }

        var currentFuel: String? = null
        val btnArea = view.findViewById<android.widget.Button>(R.id.btnSelectArea)

        fun load(fuel: String?) {
            currentFuel = fuel
            viewLifecycleOwner.lifecycleScope.launch {
                val ctx = context ?: return@launch
                val selectedArea = AreaPicker.savedId(ctx)
                val list = if (selectedArea == null) emptyList() else repo.getAutos(fuel, selectedArea).shuffled()
                adapter.submit(list)
                tvEmpty.text = if (selectedArea == null) "Select your area first to see drivers." else "No drivers found for this area and filter."
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        view.findViewById<ChipGroup>(R.id.chipGroupAutoType)
            .setOnCheckedStateChangeListener { _, checkedIds ->
                val fuel = when (checkedIds.firstOrNull()) {
                    R.id.chipEV -> "ev"
                    R.id.chipOil -> "oil"
                    else -> null
                }
                load(fuel)
            }

        fun refreshAreaLabel() {
            viewLifecycleOwner.lifecycleScope.launch {
                val ctx = context ?: return@launch
                btnArea.text = AreaPicker.label(ctx, repo)
            }
        }
        refreshAreaLabel()
        btnArea.setOnClickListener {
            AreaPicker.show(requireContext(), viewLifecycleOwner, repo) {
                refreshAreaLabel()
                load(currentFuel)
            }
        }

        load(null)
    }
}
