package com.bustime.app.ui.home

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.bustime.app.data.repository.AppRepository
import com.bustime.app.models.Place
import com.bustime.app.utils.LanguageHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * एक टेक्स्ट बॉक्स + उसके नीचे जगह के सुझाव। हिंदी, अंग्रेज़ी या पंजाबी, किसी भी भाषा में टाइप करो।
 * सुझाव पर टैप करो तो जगह चुनी जाती है; बिना टैप किए पूरा नाम लिखा हो तो भी resolve() से मिल जाती है।
 */
class PlacePicker(
    private val scope: CoroutineScope,
    private val repo: AppRepository,
    private val input: EditText,
    private val container: LinearLayout,
    private val standsOnly: Boolean,
    private val onSelected: (Place) -> Unit = {}
) {
    var selected: Place? = null
        private set

    private var suppress = false
    private var job: Job? = null

    init {
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (suppress) return
                selected = null
                job?.cancel()
                val text = s?.toString().orEmpty()
                if (text.isBlank()) {
                    container.removeAllViews()
                    return
                }
                job = scope.launch {
                    delay(150)   // टाइप करते समय बार-बार सर्च न हो
                    showSuggestions(repo.searchPlaces(text, standsOnly, 6))
                }
            }
        })
    }

    private fun showSuggestions(places: List<Place>) {
        container.removeAllViews()
        val lang = LanguageHelper.current()
        val pad = dp(14)
        for (p in places) {
            val tv = TextView(container.context).apply {
                text = p.displayName(lang)
                textSize = 16f
                setPadding(pad, dp(12), pad, dp(12))
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener { select(p) }
            }
            container.addView(tv)
        }
    }

    fun select(place: Place) {
        suppress = true
        input.setText(place.displayName(LanguageHelper.current()))
        input.setSelection(input.text.length)
        suppress = false
        selected = place
        container.removeAllViews()
        onSelected(place)
    }

    suspend fun resolve(): Place? {
        selected?.let { return it }
        val text = input.text?.toString().orEmpty()
        if (text.isBlank()) return null
        val found = repo.searchPlaces(text, standsOnly, 1).firstOrNull() ?: return null
        select(found)
        return found
    }

    private fun dp(v: Int): Int = (v * container.resources.displayMetrics.density).toInt()
}
