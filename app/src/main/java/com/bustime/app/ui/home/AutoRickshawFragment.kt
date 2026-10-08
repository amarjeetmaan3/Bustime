package com.bustime.app.ui.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.bustime.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AutoRickshawFragment : Fragment(R.layout.fragment_auto_rickshaw) {

    private var adapter: VehicleAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerViewAutos = view.findViewById<RecyclerView>(R.id.recyclerViewAutos)
        recyclerViewAutos.layoutManager = LinearLayoutManager(requireContext())
        
        val allAutos = mutableListOf("Auto 1 (EV)", "Auto 2 (Oil)", "Auto 3 (EV)", "Auto 4 (Oil)")
        val evAutos = mutableListOf("Auto 1 (EV)", "Auto 3 (EV)")
        val oilAutos = mutableListOf("Auto 2 (Oil)", "Auto 4 (Oil)")
        
        adapter = VehicleAdapter(ArrayList(allAutos))
        recyclerViewAutos.adapter = adapter

        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupAutoType)
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            
            when (checkedIds.first()) {
                R.id.chipAll -> adapter?.updateData(ArrayList(allAutos))
                R.id.chipEV -> adapter?.updateData(ArrayList(evAutos))
                R.id.chipOil -> adapter?.updateData(ArrayList(oilAutos))
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(8000)
                adapter?.rotateList()
            }
        }
    }
}
