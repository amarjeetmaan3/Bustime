package com.bustime.app.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bustime.app.R
import com.bustime.app.ads.StartIoAdsManager
import com.bustime.app.utils.ContactActions
import com.startapp.sdk.ads.nativead.NativeAdDetails

data class BusItem(
    val routeId: String,
    val time: String,
    val title: String,
    val extra: String?,
    val contact: String?
)

class BusAdapter(private val onClick: (BusItem) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var items: List<BusItem> = emptyList()
    // Each pair contains the number of route results before the ad and that ad's own SDK details.
    private var nativeAds: List<Pair<Int, NativeAdDetails>> = emptyList()
    private var nativePositions: List<Int> = emptyList()

    class RouteVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTime: TextView = view.findViewById(R.id.tvBusTime)
        val tvTitle: TextView = view.findViewById(R.id.tvBusTitle)
        val tvExtra: TextView = view.findViewById(R.id.tvBusExtra)
        val btnCall: View = view.findViewById(R.id.btnBusCall)
    }

    fun submit(list: List<BusItem>, ads: List<Pair<Int, NativeAdDetails>> = emptyList()) {
        items = list
        nativeAds = ads.sortedBy { it.first }
        nativePositions = nativeAds.mapIndexed { index, slot -> slot.first + index }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        val adIndex = nativePositions.indexOf(position)
        return if (adIndex >= 0) TYPE_NATIVE_BASE + adIndex else TYPE_ROUTE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType >= TYPE_NATIVE_BASE) {
            val adIndex = viewType - TYPE_NATIVE_BASE
            object : RecyclerView.ViewHolder(
                StartIoAdsManager.buildNativeAdView(parent.context, nativeAds[adIndex].second)
            ) {}
        } else {
            RouteVH(LayoutInflater.from(parent.context).inflate(R.layout.item_bus, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) >= TYPE_NATIVE_BASE) return
        val routeHolder = holder as RouteVH
        val itemIndex = position - nativePositions.count { it < position }
        val item = items[itemIndex]
        routeHolder.tvTime.text = item.time
        routeHolder.tvTitle.text = item.title
        routeHolder.tvExtra.text = item.extra.orEmpty()
        routeHolder.tvExtra.visibility = if (item.extra.isNullOrEmpty()) View.GONE else View.VISIBLE

        val contact = item.contact
        if (contact.isNullOrEmpty()) {
            routeHolder.btnCall.visibility = View.GONE
            routeHolder.btnCall.setOnClickListener(null)
        } else {
            routeHolder.btnCall.visibility = View.VISIBLE
            routeHolder.btnCall.setOnClickListener { ContactActions.dial(it.context, contact) }
        }
        routeHolder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size + nativeAds.size

    companion object {
        private const val TYPE_ROUTE = 0
        private const val TYPE_NATIVE_BASE = 100
    }
}
