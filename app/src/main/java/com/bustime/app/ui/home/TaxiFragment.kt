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

class TaxiFragment : Fragment(R.layout.fragment_taxi) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewTaxi)
        if (recyclerView != null) {
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            
            val drivers = mutableListOf("Taxi Driver A", "Taxi Driver B", "Taxi Driver C")
            val adapter = VehicleAdapter(drivers)
            recyclerView.adapter = adapter

            viewLifecycleOwner.lifecycleScope.launch {
                while (isActive) {
                    delay(8000)
                    adapter.rotateList()
                }
            }
        }
    }
}
