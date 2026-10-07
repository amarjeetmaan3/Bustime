package com.bustime.app.ui.search

import android.os.Handler
import android.os.Looper
import androidx.recyclerview.widget.RecyclerView
import com.bustime.app.models.Route
import java.util.Collections

class RotationalAdapter(private var routesList: MutableList<Route>) : 
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val handler = Handler(Looper.getMainLooper())
    private val rotationRunnable = object : Runnable {
        override fun run() {
            if (routesList.size > 10) {
                // लिस्ट को शफल (रोटेट) करें
                routesList.shuffle()
                notifyDataSetChanged()
                handler.postDelayed(this, 8000) // 8 सेकंड बाद फिर शफल करें
            }
        }
    }

    fun startRotation() {
        handler.postDelayed(rotationRunnable, 8000)
    }

    fun stopRotation() {
        handler.removeCallbacks(rotationRunnable)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        TODO("Implement item_vehicle_card.xml binding")
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // डेटा बाइंडिंग (प्राइस रेंज, ड्राइवर नाम आदि) यहाँ होगी
    }

    override fun getItemCount(): Int = routesList.size
    }
