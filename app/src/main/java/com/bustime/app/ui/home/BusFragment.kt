package com.bustime.app.ui.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bustime.app.R

class BusFragment : Fragment(R.layout.fragment_bus) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerViewBus = view.findViewById<RecyclerView>(R.id.recyclerViewBus)
        
        // लिस्ट को ऊपर से नीचे (Vertical) दिखाने के लिए
        recyclerViewBus.layoutManager = LinearLayoutManager(requireContext())
        
        // अडैप्टर को जोड़ना (यहाँ हम टेस्टिंग के लिए 10 कार्ड्स दिखा रहे हैं)
        recyclerViewBus.adapter = VehicleAdapter(10) 
    }
}
