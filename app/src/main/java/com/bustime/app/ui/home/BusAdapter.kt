package com.bustime.app.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bustime.app.R
import com.bustime.app.ads.AdMobAdsManager
import com.bustime.app.ui.common.applyBouncyPress
import com.bustime.app.utils.ContactActions
import com.google.android.gms.ads.nativead.NativeAd

data class BusItem(
    val routeId: String,
    val time: String,
    val title: String,
    val extra: String?,
    val contact: String?,
    val arrivalTime: String? = null,
    val durationLabel: String? = null,
    val serviceBadge: String? = null,
    val isStandMode: Boolean = false,
    val fromStopName: String? = null,
    val toStopName: String? = null,
    val routeSubtitle: String? = null,
    val metaPill: String? = null
)

class BusAdapter(private val onClick: (BusItem) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var items: List<BusItem> = emptyList()
    // Each pair contains the number of route results before the ad and that ad's own SDK details.
    private var nativeAds: List<Pair<Int, NativeAd>> = emptyList()
    private var nativePositions: List<Int> = emptyList()

    class RouteVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTime: TextView = view.findViewById(R.id.tvBusTime)
        val tvFromStop: TextView = view.findViewById(R.id.tvFromStop)
        val tvArrivalTime: TextView = view.findViewById(R.id.tvBusArrivalTime)
        val tvToStop: TextView = view.findViewById(R.id.tvToStop)
        val layoutArrivalColumn: View = view.findViewById(R.id.layoutArrivalColumn)
        val layoutDurationPill: View = view.findViewById(R.id.layoutDurationPill)
        val tvDurationPill: TextView = view.findViewById(R.id.tvDurationPill)
        val highwayTrackBox: View = view.findViewById(R.id.highwayTrackBox)
        val tvServiceBadge: TextView = view.findViewById(R.id.tvServiceBadge)
        val tvTitle: TextView = view.findViewById(R.id.tvBusTitle)
        val tvExtra: TextView = view.findViewById(R.id.tvBusExtra)
        val btnCall: View = view.findViewById(R.id.btnBusCall)
    }

    class StandVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvStandBusTitle)
        val tvRoute: TextView = view.findViewById(R.id.tvStandBusRoute)
        val tvMeta: TextView = view.findViewById(R.id.tvStandBusMeta)
        val tvTime: TextView = view.findViewById(R.id.tvStandBusTime)
        val btnCall: View = view.findViewById(R.id.btnStandBusCall)
    }

    fun submit(list: List<BusItem>, ads: List<Pair<Int, NativeAd>> = emptyList()) {
        items = list
        nativeAds = ads.sortedBy { it.first }
        nativePositions = nativeAds.mapIndexed { index, slot -> slot.first + index }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        val adIndex = nativePositions.indexOf(position)
        if (adIndex >= 0) return TYPE_NATIVE_BASE + adIndex
        val itemIndex = position - nativePositions.count { it < position }
        return if (items[itemIndex].isStandMode) TYPE_STAND else TYPE_ROUTE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when {
            viewType >= TYPE_NATIVE_BASE -> {
                val adIndex = viewType - TYPE_NATIVE_BASE
                object : RecyclerView.ViewHolder(
                    AdMobAdsManager.buildNativeAdView(parent.context, nativeAds[adIndex].second)
                ) {}
            }
            viewType == TYPE_STAND -> {
                StandVH(inflater.inflate(R.layout.item_bus_stand, parent, false))
            }
            else -> {
                RouteVH(inflater.inflate(R.layout.item_bus, parent, false))
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val viewType = getItemViewType(position)
        if (viewType >= TYPE_NATIVE_BASE) return
        val itemIndex = position - nativePositions.count { it < position }
        val item = items[itemIndex]

        if (holder is StandVH) {
            holder.tvTitle.text = item.title
            holder.tvTime.text = item.time
            if (item.routeSubtitle.isNullOrBlank()) {
                holder.tvRoute.visibility = View.GONE
            } else {
                holder.tvRoute.text = item.routeSubtitle
                holder.tvRoute.visibility = View.VISIBLE
            }
            if (item.metaPill.isNullOrBlank()) {
                holder.tvMeta.visibility = View.GONE
            } else {
                holder.tvMeta.text = item.metaPill
                holder.tvMeta.visibility = View.VISIBLE
            }
            val contact = item.contact
            if (contact.isNullOrEmpty()) {
                holder.btnCall.visibility = View.GONE
                holder.btnCall.setOnClickListener(null)
            } else {
                holder.btnCall.visibility = View.VISIBLE
                holder.btnCall.applyBouncyPress()
                holder.btnCall.setOnClickListener { ContactActions.dial(it.context, contact) }
            }
            holder.itemView.applyBouncyPress()
            holder.itemView.setOnClickListener { onClick(item) }
            return
        }

        val routeHolder = holder as RouteVH
        routeHolder.tvTime.text = item.time
        routeHolder.tvTitle.text = item.title
        routeHolder.tvFromStop.text = item.fromStopName.orEmpty()
        routeHolder.tvFromStop.visibility = if (item.fromStopName.isNullOrBlank()) View.GONE else View.VISIBLE
        routeHolder.tvToStop.text = item.toStopName.orEmpty()
        routeHolder.tvToStop.visibility = if (item.toStopName.isNullOrBlank()) View.GONE else View.VISIBLE

        if (item.serviceBadge.isNullOrBlank()) {
            routeHolder.tvServiceBadge.visibility = View.GONE
        } else {
            routeHolder.tvServiceBadge.text = item.serviceBadge
            routeHolder.tvServiceBadge.visibility = View.VISIBLE
        }

        if (item.arrivalTime.isNullOrBlank()) {
            routeHolder.layoutArrivalColumn.visibility = View.GONE
            routeHolder.highwayTrackBox.visibility = View.GONE
        } else {
            routeHolder.tvArrivalTime.text = item.arrivalTime
            routeHolder.layoutArrivalColumn.visibility = View.VISIBLE
            routeHolder.highwayTrackBox.visibility = View.VISIBLE
            if (item.durationLabel.isNullOrBlank()) {
                routeHolder.layoutDurationPill.visibility = View.GONE
            } else {
                routeHolder.tvDurationPill.text = item.durationLabel
                routeHolder.layoutDurationPill.visibility = View.VISIBLE
            }
        }

        routeHolder.tvExtra.text = item.extra.orEmpty()
        routeHolder.tvExtra.visibility = if (item.extra.isNullOrEmpty()) View.GONE else View.VISIBLE

        val contact = item.contact
        if (contact.isNullOrEmpty()) {
            routeHolder.btnCall.visibility = View.GONE
            routeHolder.btnCall.setOnClickListener(null)
        } else {
            routeHolder.btnCall.visibility = View.VISIBLE
            routeHolder.btnCall.applyBouncyPress()
            routeHolder.btnCall.setOnClickListener { ContactActions.dial(it.context, contact) }
        }
        routeHolder.itemView.applyBouncyPress()
        routeHolder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size + nativeAds.size

    companion object {
        private const val TYPE_ROUTE = 0
        private const val TYPE_STAND = 1
        private const val TYPE_NATIVE_BASE = 100
    }
}
