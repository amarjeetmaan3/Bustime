package com.bustime.app.ui.home

import android.os.Bundle
import android.view.View
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bustime.app.R
import com.bustime.app.data.repository.AppRepository
import com.google.android.material.button.MaterialButton
import com.bustime.app.utils.AnalyticsHelper
import kotlinx.coroutines.launch

/** पहले सीटर चुनो (4, 5, 6, 7...), फिर उसी सीटर के ड्राइवरों की नई लिस्ट */
class TaxiFragment : Fragment(R.layout.fragment_taxi) {

    private lateinit var repo: AppRepository
    private lateinit var adapter: DriverAdapter
    private lateinit var layoutSeaters: View
    private lateinit var layoutDrivers: View
    private lateinit var seatersContainer: LinearLayout
    private lateinit var seatFiltersContainer: LinearLayout
    private lateinit var tvSeatersEmpty: TextView
    private lateinit var btnArea: Button
    private var currentSeats: Int? = null   // None = सीटर वाली स्क्रीन खुली है
    private lateinit var tvNoDrivers: TextView
    private lateinit var backCallback: OnBackPressedCallback

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = AppRepository.getInstance(requireContext())
        AnalyticsHelper.logEvent(requireContext(), "section_open", "taxi")

        layoutSeaters = view.findViewById(R.id.layoutSeaters)
        layoutDrivers = view.findViewById(R.id.layoutDrivers)
        seatersContainer = view.findViewById(R.id.seatersContainer)
        seatFiltersContainer = view.findViewById(R.id.seatFiltersContainer)
        val seatFiltersScroll = view.findViewById<HorizontalScrollView>(R.id.seatFiltersScroll)
        // Keep horizontal seat-filter gestures inside this row; don't let the pager switch tabs.
        seatFiltersScroll.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                    v.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
        tvSeatersEmpty = view.findViewById(R.id.tvSeatersEmpty)
        btnArea = view.findViewById(R.id.btnSelectArea)
        tvNoDrivers = view.findViewById(R.id.tvNoDrivers)

        val recycler = view.findViewById<RecyclerView>(R.id.recyclerViewTaxi)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        adapter = DriverAdapter()
        recycler.adapter = adapter

        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = showSeaters()
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        refreshAreaLabel()
        btnArea.setOnClickListener {
            AreaPicker.show(requireContext(), viewLifecycleOwner, repo) {
                refreshAreaLabel()
                currentSeats?.let { showDrivers(it) }
            }
        }
        view.findViewById<Button>(R.id.btnAddTaxi).setOnClickListener {
            startActivity(android.content.Intent(requireContext(), com.bustime.app.ui.community.CommunityRequestActivity::class.java).putExtra("mode", "vehicle").putExtra("vehicle_type", "taxi"))
        }

        showSeaters()
    }

    // सीटर चुनने वाली स्क्रीन: विकल्प डेटा (routes.json के taxiSeaters) से बनते हैं
    private fun showSeaters() {
        backCallback.isEnabled = false
        currentSeats = null
        layoutDrivers.visibility = View.GONE
        layoutSeaters.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val seats = repo.getSeaterGroups()
            val ctx = context ?: return@launch
            seatersContainer.removeAllViews()
            seatFiltersContainer.removeAllViews()
            tvSeatersEmpty.visibility = if (seats.isEmpty()) View.VISIBLE else View.GONE

            val margin = (8 * resources.displayMetrics.density).toInt()
            for (n in seats) {
                val filter = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = getString(R.string.seater_format, n)
                    textSize = 11f
                    minHeight = (36 * resources.displayMetrics.density).toInt()
                    minimumHeight = minHeight
                    setPadding((10 * resources.displayMetrics.density).toInt(), 0, (10 * resources.displayMetrics.density).toInt(), 0)
                    isAllCaps = false
                    setOnClickListener {
                        AnalyticsHelper.logEvent(requireContext(), "taxi_seat_filter", "taxi", org.json.JSONObject().put("seats", n))
                        showDrivers(n)
                    }
                }
                val filterLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (38 * resources.displayMetrics.density).toInt())
                filterLp.marginEnd = margin
                seatFiltersContainer.addView(filter, filterLp)

                val button = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = getString(R.string.seater_format, n)
                    textSize = 18f
                    setOnClickListener { showDrivers(n) }
                }
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = margin
                seatersContainer.addView(button, lp)
            }
        }
    }

    // चुने हुए सीटर के ड्राइवर (हर बार क्रम बदलकर, ताकि सबको बराबर मौका मिले)
    private fun showDrivers(seats: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = context ?: return@launch
            val selectedArea = AreaPicker.savedId(ctx)
            AnalyticsHelper.logEvent(ctx, "taxi_list_view", "taxi", org.json.JSONObject().put("seats", seats).put("area_selected", selectedArea != null))
            val list = if (selectedArea == null) emptyList() else repo.getTaxis(seats, selectedArea).shuffled()
            currentSeats = seats
            adapter.submit(list)
            tvNoDrivers.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            layoutSeaters.visibility = View.GONE
            layoutDrivers.visibility = View.VISIBLE
            backCallback.isEnabled = true
        }
    }

    private fun refreshAreaLabel() {
        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = context ?: return@launch
            btnArea.text = AreaPicker.label(ctx, repo)
            if (AreaPicker.savedId(ctx) == null) tvNoDrivers.text = getString(R.string.taxi_area_required)
            else tvNoDrivers.text = getString(R.string.taxi_no_area_drivers)
        }
    }
}
