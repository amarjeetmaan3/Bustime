package com.bustime.app.ui.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bustime.app.R

class TaxiFragment : Fragment(R.layout.fragment_taxi) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerViewTaxi = view.findViewById<RecyclerView>(R.id.recyclerViewTaxi)
        
        // लिस्ट को ऊपर से नीचे सेट करना
        recyclerViewTaxi.layoutManager = LinearLayoutManager(requireContext())
        
        // टेस्टिंग के लिए टैक्सी टैब में 5 कार्ड्स दिखा रहे हैं
        recyclerViewTaxi.adapter = VehicleAdapter(5) 
    }
}
