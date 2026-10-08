package com.bustime.app.ui.home

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.bustime.app.R
import com.bustime.app.data.repository.AppRepository
import kotlinx.coroutines.launch

/** सारे ऑटो एक लिस्ट में; ऊपर All | EV | Oil */
class AutoRickshawFragment : Fragment(R.layout.fragment_auto_rickshaw) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val repo = AppRepository.getInstance(requireContext())

        val recycler = view.findViewById<RecyclerView>(R.id.recyclerViewAutos)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        val adapter = DriverAdapter()
        recycler.adapter = adapter
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmptyAuto)

        fun load(fuel: String?) {
            viewLifecycleOwner.lifecycleScope.launch {
                val list = repo.getAutos(fuel).shuffled()   // हर बार क्रम बदलकर, सबको बराबर मौका
                adapter.submit(list)
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        view.findViewById<ChipGroup>(R.id.chipGroupAutoType)
            .setOnCheckedStateChangeListener { _, checkedIds ->
                val fuel = when (checkedIds.firstOrNull()) {
                    R.id.chipEV -> "ev"
                    R.id.chipOil -> "oil"
                    else -> null
                }
                load(fuel)
            }

        load(null)
    }
}
