package com.bustime.app.ui.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bustime.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BusFragment : Fragment(R.layout.fragment_bus) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerViewBus = view.findViewById<RecyclerView>(R.id.recyclerViewBus)
        recyclerViewBus.layoutManager = LinearLayoutManager(requireContext())
        
        val drivers = mutableListOf("Bus Driver 1", "Bus Driver 2", "Bus Driver 3", "Bus Driver 4", "Bus Driver 5")
        val adapter = VehicleAdapter(drivers)
        recyclerViewBus.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(8000)
                adapter.rotateList()
            }
        }
    }
}
