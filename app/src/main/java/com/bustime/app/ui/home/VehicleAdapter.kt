package com.bustime.app.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bustime.app.R
import com.bustime.app.ui.details.ContactDriverHelper

class VehicleAdapter(private var driverList: MutableList<String>) : 
    RecyclerView.Adapter<VehicleAdapter.VehicleViewHolder>() {

    private var contactHelper: ContactDriverHelper? = null

    class VehicleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvDriverOrCompanyName)
        val tvTiming: TextView = view.findViewById(R.id.tvTiming)
        val tvPrice: TextView = view.findViewById(R.id.tvPriceRange)
        val btnShowNumber: Button = view.findViewById(R.id.btnShowNumber)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        contactHelper = ContactDriverHelper(recyclerView.context)
    }

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
        val driverName = driverList[position]
        
        holder.tvName.text = driverName
        holder.tvTiming.text = "Departure: 10:30 AM"
        holder.tvPrice.text = "Price: ₹50 - ₹80"
        
        holder.btnShowNumber.setOnClickListener {
            contactHelper?.playDisclaimerAndShowNumber(driverName, "+91-9876543210")
        }
    }

    override fun getItemCount(): Int = driverList.size

    // 8-सेकंड वाला शफलिंग लॉजिक
    fun rotateList() {
        if (driverList.size > 1) {
            val firstItem = driverList.removeAt(0)
            driverList.add(firstItem)
            notifyItemRemoved(0)
            notifyItemInserted(driverList.size - 1)
        }
    }

    // चिप्स (फिल्टर) के लिए डेटा अपडेट करने का लॉजिक
    fun updateData(newList: MutableList<String>) {
        driverList = ArrayList(newList)
        notifyDataSetChanged()
    }
    }
