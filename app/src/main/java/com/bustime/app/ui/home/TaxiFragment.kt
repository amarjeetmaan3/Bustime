package com.bustime.app.ui.home

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bustime.app.R
import com.bustime.app.data.repository.AppRepository
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/** पहले सीटर चुनो (4, 5, 6, 7...), फिर उसी सीटर के ड्राइवरों की नई लिस्ट */
class TaxiFragment : Fragment(R.layout.fragment_taxi) {

    private lateinit var repo: AppRepository
    private lateinit var adapter: DriverAdapter
    private lateinit var layoutSeaters: View
    private lateinit var layoutDrivers: View
    private lateinit var seatersContainer: LinearLayout
    private lateinit var tvSeatersEmpty: TextView
    private lateinit var btnArea: Button
    private var currentSeats: Int? = null   // None = सीटर वाली स्क्रीन खुली है
    private lateinit var tvNoDrivers: TextView
    private lateinit var backCallback: OnBackPressedCallback

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = AppRepository.getInstance(requireContext())

        layoutSeaters = view.findViewById(R.id.layoutSeaters)
        layoutDrivers = view.findViewById(R.id.layoutDrivers)
        seatersContainer = view.findViewById(R.id.seatersContainer)
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
            tvSeatersEmpty.visibility = if (seats.isEmpty()) View.VISIBLE else View.GONE

            val margin = (12 * resources.displayMetrics.density).toInt()
            for (n in seats) {
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
            if (AreaPicker.savedId(ctx) == null) tvNoDrivers.text = "Select your area first to see drivers."
            else tvNoDrivers.text = "No drivers found for this area and seat type."
        }
    }
}
