package com.bustime.app.ui.home

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bustime.app.R
import com.bustime.app.data.repository.AppRepository
import com.bustime.app.models.Place
import com.bustime.app.ui.search.PlacePickerActivity
import com.bustime.app.ui.search.SearchActivity
import com.bustime.app.utils.LanguageHelper
import kotlinx.coroutines.launch

/**
 * बॉक्स पर टैप करो -> जगह चुनने का पूरा पेज खुलता है (कीबोर्ड के साथ)।
 * बीच का गोल बटन From और To की जगहें आपस में बदल देता है। नतीजे अलग पेज पर खुलते हैं।
 */
class BusFragment : Fragment(R.layout.fragment_bus) {

    private lateinit var repo: AppRepository
    private lateinit var etFrom: EditText
    private lateinit var etTo: EditText
    private lateinit var etStand: EditText

    private var fromPlace: Place? = null
    private var toPlace: Place? = null
    private var pickTarget = ""   // "from", "to" या "stand"

    private val pickLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val id = result.data?.getStringExtra("PLACE_ID")
            if (id != null) onPicked(pickTarget, id)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = AppRepository.getInstance(requireContext())

        etFrom = view.findViewById(R.id.etFrom)
        etTo = view.findViewById(R.id.etTo)
        etStand = view.findViewById(R.id.etStand)

        etFrom.setOnClickListener { openPicker("from", getString(R.string.from_hint), false) }
        etTo.setOnClickListener { openPicker("to", getString(R.string.to_hint), false) }
        etStand.setOnClickListener { openPicker("stand", getString(R.string.stand_hint), true) }

        view.findViewById<View>(R.id.btnSwap).setOnClickListener {
            val t = fromPlace
            fromPlace = toPlace
            toPlace = t
            showTexts()
        }

        view.findViewById<View>(R.id.btnSearchRoute).setOnClickListener { searchRoutes() }

        // भाषा बदलने पर स्क्रीन दोबारा बनती है: चुनी हुई जगहें वापस भरो
        val savedFrom = savedInstanceState?.getString("FROM_ID")
        val savedTo = savedInstanceState?.getString("TO_ID")
        if (savedFrom != null || savedTo != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val ids = listOfNotNull(savedFrom, savedTo).toSet()
                val found = repo.getPlacesById(ids)
                fromPlace = savedFrom?.let { found[it] }
                toPlace = savedTo?.let { found[it] }
                showTexts()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("FROM_ID", fromPlace?.id)
        outState.putString("TO_ID", toPlace?.id)
    }

    private fun openPicker(target: String, hint: String, standsOnly: Boolean) {
        pickTarget = target
        pickLauncher.launch(
            Intent(requireContext(), PlacePickerActivity::class.java)
                .putExtra("HINT", hint)
                .putExtra("STANDS_ONLY", standsOnly)
        )
    }

    private fun onPicked(target: String, placeId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val place = repo.getPlacesById(setOf(placeId))[placeId] ?: return@launch
            when (target) {
                "from" -> fromPlace = place
                "to" -> toPlace = place
                "stand" -> {
                    etStand.setText(place.displayName(LanguageHelper.current()))
                    openStandResults(place)
                }
            }
            showTexts()
        }
    }

    private fun showTexts() {
        val lang = LanguageHelper.current()
        etFrom.setText(fromPlace?.displayName(lang).orEmpty())
        etTo.setText(toPlace?.displayName(lang).orEmpty())
    }

    // From -> To: नतीजे अगले पेज पर
    private fun searchRoutes() {
        val ctx = context ?: return
        val from = fromPlace
        val to = toPlace
        if (from == null || to == null) {
            Toast.makeText(ctx, R.string.select_places, Toast.LENGTH_SHORT).show()
            return
        }
        if (from.id == to.id) {
            Toast.makeText(ctx, R.string.same_place, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(ctx, SearchActivity::class.java)
                .putExtra("MODE", "route")
                .putExtra("FROM_ID", from.id)
                .putExtra("TO_ID", to.id)
        )
    }

    // बस स्टैंड: नतीजे अगले पेज पर
    private fun openStandResults(place: Place) {
        val ctx = context ?: return
        startActivity(
            Intent(ctx, SearchActivity::class.java)
                .putExtra("MODE", "stand")
                .putExtra("STAND_ID", place.id)
        )
    }
}
