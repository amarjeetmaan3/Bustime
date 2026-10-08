package com.bustime.app.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bustime.app.R

// यह अडैप्टर हमारे item_vehicle_card को लिस्ट में बार-बार दिखाएगा
class VehicleAdapter(private val itemCount: Int) : 
    RecyclerView.Adapter<VehicleAdapter.VehicleViewHolder>() {

    class VehicleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvDriverOrCompanyName)
        val tvTiming: TextView = view.findViewById(R.id.tvTiming)
        val tvPrice: TextView = view.findViewById(R.id.tvPriceRange)
        val btnShowNumber: Button = view.findViewById(R.id.btnShowNumber)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VehicleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vehicle_card, parent, false)
        return VehicleViewHolder(view)
    }

    override fun onBindViewHolder(holder: VehicleViewHolder, position: Int) {
        // अभी हम डमी (Testing) डेटा दिखा रहे हैं, बाद में यह रूम डेटाबेस (Delta Sync) से आएगा
        holder.tvName.text = "Vehicle/Driver ${position + 1}"
        holder.tvTiming.text = "Departure: 10:30 AM"
        holder.tvPrice.text = "Price: ₹50 - ₹80"
        
        holder.btnShowNumber.setOnClickListener {
            // यहाँ बाद में AI Voice वाला कोड चलेगा
        }
    }

    override fun getItemCount(): Int {
        return itemCount // कितनी गाड़ियां दिखानी हैं
    }
    }
