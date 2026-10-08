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
        holder.tvName.text = "Vehicle/Driver ${position + 1}"
        holder.tvTiming.text = "Departure: 10:30 AM"
        holder.tvPrice.text = "Price: ₹50 - ₹80"
        
        // बटन क्लिक करने का कोड
        holder.btnShowNumber.setOnClickListener {
            // अभी हम एक मैसेज दिखा रहे हैं, बाद में यहाँ ContactDriverHelper कॉल होगा
            android.widget.Toast.makeText(
                holder.itemView.context, 
                "Driver ${position + 1} Selected! AI Voice will play here.", 
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun getItemCount(): Int {
        return itemCount // कितनी गाड़ियां दिखानी हैं
    }
    }
