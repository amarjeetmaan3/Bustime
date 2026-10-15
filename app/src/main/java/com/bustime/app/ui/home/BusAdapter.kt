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
    private var nativeAd: NativeAdDetails? = null
    private var nativePosition = -1

    class RouteVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTime: TextView = view.findViewById(R.id.tvBusTime)
        val tvTitle: TextView = view.findViewById(R.id.tvBusTitle)
        val tvExtra: TextView = view.findViewById(R.id.tvBusExtra)
        val btnCall: View = view.findViewById(R.id.btnBusCall)
    }

    fun submit(list: List<BusItem>, ad: NativeAdDetails? = null, insertAfter: Int = 4) {
        items = list
        nativeAd = ad?.takeIf { list.size > insertAfter && insertAfter > 0 }
        nativePosition = if (nativeAd != null) insertAfter else -1
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int =
        if (position == nativePosition) TYPE_NATIVE else TYPE_ROUTE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_NATIVE) {
            object : RecyclerView.ViewHolder(
                StartIoAdsManager.buildNativeAdView(parent.context, requireNotNull(nativeAd))
            ) {}
        } else {
            RouteVH(LayoutInflater.from(parent.context).inflate(R.layout.item_bus, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == TYPE_NATIVE) return
        val routeHolder = holder as RouteVH
        val itemIndex = if (nativePosition >= 0 && position > nativePosition) position - 1 else position
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

    override fun getItemCount(): Int = items.size + if (nativePosition >= 0) 1 else 0

    companion object {
        private const val TYPE_ROUTE = 0
        private const val TYPE_NATIVE = 1
    }
}
