package com.bustime.app.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bustime.app.R
import com.bustime.app.ui.details.ContactDriverHelper

class VehicleAdapter(private val itemCount: Int) : 
    RecyclerView.Adapter<VehicleAdapter.VehicleViewHolder>() {

    // हमारा नया AI Voice Helper
    private var contactHelper: ContactDriverHelper? = null

    class VehicleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvDriverOrCompanyName)
        val tvTiming: TextView = view.findViewById(R.id.tvTiming)
        val tvPrice: TextView = view.findViewById(R.id.tvPriceRange)
        val btnShowNumber: Button = view.findViewById(R.id.btnShowNumber)
    }

    // जब लिस्ट स्क्रीन पर आती है, तब वॉइस इंजन को रेडी कर लेते हैं
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        contactHelper = ContactDriverHelper(recyclerView.context)
    }

    // जब लिस्ट स्क्रीन से हटती है, तो वॉइस इंजन को बंद कर देते हैं
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        contactHelper?.stopVoice()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VehicleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vehicle_card, parent, false)
        return VehicleViewHolder(view)
    }

    override fun onBindViewHolder(holder: VehicleViewHolder, position: Int) {
        val driverName = "Driver ${position + 1}"
        
        holder.tvName.text = driverName
        holder.tvTiming.text = "Departure: 10:30 AM"
        holder.tvPrice.text = "Price: ₹50 - ₹80"
        
        // बटन क्लिक करने पर AI Voice प्ले होगी
        holder.btnShowNumber.setOnClickListener {
            contactHelper?.playDisclaimerAndShowNumber(driverName, "+91-9876543210")
        }
    }

    override fun getItemCount(): Int {
        return itemCount
    }
    }
