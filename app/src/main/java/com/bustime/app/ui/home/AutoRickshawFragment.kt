package com.bustime.app.ui.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.bustime.app.R

class AutoRickshawFragment : Fragment(R.layout.fragment_auto_rickshaw) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerViewAutos = view.findViewById<RecyclerView>(R.id.recyclerViewAutos)
        recyclerViewAutos.layoutManager = LinearLayoutManager(requireContext())
        
        // ऐप खुलते ही डिफ़ॉल्ट रूप से 'All' सेलेक्ट रहता है, तो हम 15 कार्ड्स दिखाएंगे
        recyclerViewAutos.adapter = VehicleAdapter(15)

        // चिप्स (फिल्टर बटन) का क्लिक लॉजिक
        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupAutoType)
        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            
            // जो चिप सेलेक्ट हुआ है, उसके हिसाब से लिस्ट को अपडेट करना (फिल्टर इफ़ेक्ट)
            when (checkedIds.first()) {
                R.id.chipAll -> {
                    recyclerViewAutos.adapter = VehicleAdapter(15) // सारे 15 कार्ड्स
                }
                R.id.chipEV -> {
                    recyclerViewAutos.adapter = VehicleAdapter(5)  // सिर्फ 5 EV कार्ड्स
                }
                R.id.chipOil -> {
                    recyclerViewAutos.adapter = VehicleAdapter(10) // सिर्फ 10 Oil कार्ड्स
                }
            }
        }
    }
}
