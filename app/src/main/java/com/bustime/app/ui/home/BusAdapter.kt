package com.bustime.app.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bustime.app.R
import com.bustime.app.utils.ContactActions

data class BusItem(
    val routeId: String,
    val time: String,        // दिखाने वाला समय, जैसे "6:30 AM"
    val title: String,       // "From → To"
    val extra: String?,      // पहुँचने का समय, सर्विस का नाम
    val contact: String?
)

class BusAdapter(private val onClick: (BusItem) -> Unit) : RecyclerView.Adapter<BusAdapter.VH>() {

    private var items: List<BusItem> = emptyList()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTime: TextView = view.findViewById(R.id.tvBusTime)
        val tvTitle: TextView = view.findViewById(R.id.tvBusTitle)
        val tvExtra: TextView = view.findViewById(R.id.tvBusExtra)
        val btnCall: Button = view.findViewById(R.id.btnBusCall)
    }

    fun submit(list: List<BusItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_bus, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvTime.text = item.time
        holder.tvTitle.text = item.title
        holder.tvExtra.text = item.extra.orEmpty()
        holder.tvExtra.visibility = if (item.extra.isNullOrEmpty()) View.GONE else View.VISIBLE

        val contact = item.contact
        if (contact.isNullOrEmpty()) {
            holder.btnCall.visibility = View.GONE
        } else {
            holder.btnCall.visibility = View.VISIBLE
            holder.btnCall.setOnClickListener { ContactActions.dial(it.context, contact) }
        }
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
