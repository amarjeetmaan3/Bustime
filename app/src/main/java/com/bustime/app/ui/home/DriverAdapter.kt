package com.bustime.app.ui.home

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bustime.app.R
import com.bustime.app.models.Driver
import com.bustime.app.utils.ContactActions

/**
 * टैक्सी और ऑटो की लिस्ट। मोबाइल नंबर पहले छिपा रहता है:
 * "Show number" दबाओ -> छोटा संदेश -> 3 सेकंड बाद नंबर + Call / WhatsApp।
 */
class DriverAdapter : RecyclerView.Adapter<DriverAdapter.VH>() {

    private var items: List<Driver> = emptyList()
    private val pending = HashSet<String>()    // संदेश दिख रहा है, नंबर अभी नहीं
    private val revealed = HashSet<String>()   // नंबर खुल चुका है
    private val handler = Handler(Looper.getMainLooper())

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvDriverName)
        val tvVehicle: TextView = view.findViewById(R.id.tvDriverVehicle)
        val tvMeta: TextView = view.findViewById(R.id.tvDriverMeta)
        val tvInfo: TextView = view.findViewById(R.id.tvDriverInfo)
        val btnShow: Button = view.findViewById(R.id.btnShowNumber)
        val tvNotice: TextView = view.findViewById(R.id.tvNumberNotice)
        val layoutRevealed: View = view.findViewById(R.id.layoutRevealed)
        val tvNumber: TextView = view.findViewById(R.id.tvDriverNumber)
        val btnCall: Button = view.findViewById(R.id.btnDriverCall)
        val btnWhatsApp: Button = view.findViewById(R.id.btnDriverWhatsApp)
    }

    fun submit(list: List<Driver>) {
        handler.removeCallbacksAndMessages(null)
        pending.clear()
        revealed.clear()
        items = list
        notifyDataSetChanged()
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        handler.removeCallbacksAndMessages(null)
        pending.clear()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_driver, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val d = items[position]
        val ctx = holder.itemView.context

        holder.tvName.text = d.name
        setOptional(holder.tvVehicle, d.vehicle)
        setOptional(holder.tvInfo, d.info)

        val seats = d.seats
        val meta = when {
            seats != null -> ctx.getString(R.string.seater_format, seats)
            d.fuel == "ev" -> ctx.getString(R.string.auto_ev)
            d.fuel == "oil" -> ctx.getString(R.string.auto_oil)
            else -> null
        }
        setOptional(holder.tvMeta, meta)

        // तीन हालतें: छिपा नंबर / संदेश दिख रहा / नंबर खुला
        when {
            d.id in revealed -> {
                holder.btnShow.visibility = View.GONE
                holder.tvNotice.visibility = View.GONE
                holder.layoutRevealed.visibility = View.VISIBLE
                holder.tvNumber.text = d.phone
                holder.btnCall.setOnClickListener { ContactActions.dial(it.context, d.phone) }
                holder.btnWhatsApp.setOnClickListener { ContactActions.whatsapp(it.context, d.phone) }
            }
            d.id in pending -> {
                holder.btnShow.visibility = View.GONE
                holder.layoutRevealed.visibility = View.GONE
                holder.tvNotice.visibility = View.VISIBLE
            }
            else -> {
                holder.btnShow.visibility = View.VISIBLE
                holder.tvNotice.visibility = View.GONE
                holder.layoutRevealed.visibility = View.GONE
                holder.btnShow.setOnClickListener { startReveal(d.id) }
            }
        }
    }

    private fun startReveal(id: String) {
        pending.add(id)
        notifyItemChanged(items.indexOfFirst { it.id == id })
        handler.postDelayed({
            pending.remove(id)
            revealed.add(id)
            val idx = items.indexOfFirst { it.id == id }
            if (idx >= 0) notifyItemChanged(idx)
        }, REVEAL_DELAY_MS)
    }

    private fun setOptional(tv: TextView, text: String?) {
        if (text.isNullOrEmpty()) {
            tv.visibility = View.GONE
        } else {
            tv.visibility = View.VISIBLE
            tv.text = text
        }
    }

    override fun getItemCount(): Int = items.size

    companion object {
        private const val REVEAL_DELAY_MS = 3000L
    }
}
