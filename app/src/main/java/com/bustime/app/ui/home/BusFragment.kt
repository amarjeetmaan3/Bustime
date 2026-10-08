package com.bustime.app.ui.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bustime.app.R
import com.bustime.app.data.repository.AppRepository
import com.bustime.app.models.Place
import com.bustime.app.ui.search.SearchActivity
import kotlinx.coroutines.launch

/** यहाँ सिर्फ़ खोजने के बॉक्स हैं; नतीजे अलग पेज (SearchActivity) पर खुलते हैं */
class BusFragment : Fragment(R.layout.fragment_bus) {

    private lateinit var fromPicker: PlacePicker
    private lateinit var toPicker: PlacePicker
    private lateinit var standPicker: PlacePicker

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val repo = AppRepository.getInstance(requireContext())
        val scope = viewLifecycleOwner.lifecycleScope

        val etStand = view.findViewById<EditText>(R.id.etStand)
        fromPicker = PlacePicker(scope, repo, view.findViewById(R.id.etFrom), view.findViewById<LinearLayout>(R.id.suggestFrom), false)
        toPicker = PlacePicker(scope, repo, view.findViewById(R.id.etTo), view.findViewById<LinearLayout>(R.id.suggestTo), false)
        standPicker = PlacePicker(scope, repo, etStand, view.findViewById<LinearLayout>(R.id.suggestStand), true) { place ->
            openStandResults(place)
        }

        view.findViewById<Button>(R.id.btnSearchRoute).setOnClickListener { searchRoutes() }

        // कीबोर्ड का Search बटन: पूरा स्टैंड नाम लिखा हो तो भी चलेगा
        etStand.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                scope.launch { standPicker.resolve() }
                true
            } else false
        }
    }

    // From -> To: नतीजे अगले पेज पर
    private fun searchRoutes() {
        viewLifecycleOwner.lifecycleScope.launch {
            val from = fromPicker.resolve()
            val to = toPicker.resolve()
            val ctx = context ?: return@launch
            if (from == null || to == null) {
                Toast.makeText(ctx, R.string.select_places, Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (from.id == to.id) {
                Toast.makeText(ctx, R.string.same_place, Toast.LENGTH_SHORT).show()
                return@launch
            }
            hideKeyboard()
            startActivity(
                Intent(ctx, SearchActivity::class.java)
                    .putExtra("MODE", "route")
                    .putExtra("FROM_ID", from.id)
                    .putExtra("TO_ID", to.id)
            )
        }
    }

    // बस स्टैंड: नतीजे अगले पेज पर
    private fun openStandResults(place: Place) {
        val ctx = context ?: return
        hideKeyboard()
        startActivity(
            Intent(ctx, SearchActivity::class.java)
                .putExtra("MODE", "stand")
                .putExtra("STAND_ID", place.id)
        )
    }

    private fun hideKeyboard() {
        val v = view ?: return
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(v.windowToken, 0)
        v.findFocus()?.clearFocus()
    }
}
