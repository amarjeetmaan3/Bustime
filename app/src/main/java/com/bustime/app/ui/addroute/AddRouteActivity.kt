package com.bustime.app.ui.addroute

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Filter
import android.widget.Filterable
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bustime.app.R
import com.bustime.app.data.repository.AppRepository
import com.bustime.app.models.Route
import com.bustime.app.utils.LanguageHelper
import com.bustime.app.utils.TimeFlow
import com.bustime.app.utils.TimeFormat
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * ऊपर दो विकल्प:
 *  Existing Route = पहले से बने रूट की दूसरी बस / अलग समय (रूट चुनो, समय भरो)
 *  New Route      = बिल्कुल नया रूट (From, To और बीच के पड़ाव, किसी भी भाषा में लिखो)
 * समय का क्रम TimeFlow से जाँचा जाता है: आधी रात के बाद का समय अगले दिन का माना जाता है
 * (कोई Long Route चेकबॉक्स या तारीख नहीं)। सब कुछ पहले एडमिन के रिव्यू में जाता है।
 */
class AddRouteActivity : AppCompatActivity() {
    private class ExistingStop(val placeId: String?, val timeView: TextView, var time: String? = null, val nameEdit: EditText? = null, var line: View? = null)
    private class NewStop(val name: EditText, val timeView: TextView, var time: String? = null)
    private data class PlaceSuggestion(
        val placeId: String,
        val namesByLanguage: Map<String, String>,
        val fallbackName: String,
        val selectedName: String,
        val label: String,
        val searchableNames: List<String>
    ) {
        override fun toString(): String = label
    }

    private var placeSuggestions: List<PlaceSuggestion> = emptyList()

    private fun languageOrder(lang: String): List<String> = when (lang) {
        "hi" -> listOf("hi", "en", "pa")
        "pa" -> listOf("pa", "hi", "en")
        else -> listOf("en", "hi", "pa")
    }

    /**
     * Android doesn't reliably expose the active keyboard language. Detect the
     * script the user is actually typing, and fall back to the app language when
     * the field is empty or the text has no recognizable script.
     */
    private fun typedLanguage(query: String, fallback: String): String {
        val punjabi = query.count { it.code in 0x0A00..0x0A7F }
        val hindi = query.count { it.code in 0x0900..0x097F }
        val english = query.count { it in 'A'..'Z' || it in 'a'..'z' }
        return when {
            punjabi > hindi && punjabi > english -> "pa"
            hindi > punjabi && hindi > english -> "hi"
            english > 0 && english >= hindi && english >= punjabi -> "en"
            hindi > 0 -> "hi"
            punjabi > 0 -> "pa"
            else -> fallback
        }
    }

    private fun suggestionForLanguage(item: PlaceSuggestion, lang: String): PlaceSuggestion {
        val orderedNames = languageOrder(lang).mapNotNull { code ->
            item.namesByLanguage[code]?.trim()?.takeIf { it.isNotBlank() }
        }.distinct()
        val selected = item.namesByLanguage[lang]?.trim()?.takeIf { it.isNotBlank() }
            ?: languageOrder(lang).firstNotNullOfOrNull { code ->
                item.namesByLanguage[code]?.trim()?.takeIf { it.isNotBlank() }
            }
            ?: item.fallbackName
        return item.copy(
            selectedName = selected,
            label = (orderedNames.ifEmpty { listOf(selected) }).joinToString(" • ")
        )
    }

    /**
     * Dedicated read-only adapter for multilingual place suggestions.
     * It never mutates ArrayAdapter's backing collection while Android's Filter is
     * publishing results, which keeps dynamically-created stop fields safe.
     */
    private inner class PlaceSuggestionAdapter(items: List<PlaceSuggestion>) :
        BaseAdapter(), Filterable {
        private val allItems: List<PlaceSuggestion> = items.toList()

        @Volatile
        private var displayedItems: List<PlaceSuggestion> = allItems

        override fun getCount(): Int = displayedItems.size

        override fun getItem(position: Int): PlaceSuggestion = displayedItems[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup?): View {
            val row = (convertView as? TextView)
                ?: layoutInflater.inflate(android.R.layout.simple_dropdown_item_1line, parent, false) as TextView
            row.text = getItem(position).label
            return row
        }

        override fun getFilter(): Filter = object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val query = constraint?.toString()?.trim()?.lowercase()
                    ?.replace(Regex("\\s+"), " ").orEmpty()
                val lang = typedLanguage(query, LanguageHelper.current())
                val matches = (if (query.isBlank()) allItems else allItems.filter { item ->
                    item.searchableNames.any { name -> name.lowercase().contains(query) }
                }).map { item -> suggestionForLanguage(item, lang) }
                return FilterResults().apply {
                    values = matches
                    count = matches.size
                }
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                displayedItems = (results?.values as? List<PlaceSuggestion>).orEmpty()
                notifyDataSetChanged()
            }

            override fun convertResultToString(resultValue: Any): CharSequence =
                (resultValue as? PlaceSuggestion)?.label.orEmpty()
        }
    }

    private fun placeSuggestionAdapter(): PlaceSuggestionAdapter = PlaceSuggestionAdapter(placeSuggestions)

    private fun attachPlaceSuggestions(field: AutoCompleteTextView) {
        field.threshold = 1
        field.setAdapter(placeSuggestionAdapter())
        field.setOnItemClickListener { parent, _, position, _ ->
            val suggestion = parent.getItemAtPosition(position) as? PlaceSuggestion
            if (suggestion != null) {
                // Show only the selected-language name in the field; the dropdown
                // itself keeps all available translations together on one line.
                field.setText(suggestion.selectedName, false)
                field.setSelection(field.text.length)
            }
        }
        field.setOnFocusChangeListener { _, focused ->
            if (focused && placeSuggestions.isNotEmpty()) field.showDropDown()
        }
        field.setOnClickListener { if (placeSuggestions.isNotEmpty()) field.showDropDown() }
    }

    private fun placeAutoComplete(hintText: String): AutoCompleteTextView = AutoCompleteTextView(this).apply {
        hint = hintText
        attachPlaceSuggestions(this)
    }

    private fun refreshPlaceSuggestions() {
        lifecycleScope.launch {
            val all = withContext(Dispatchers.IO) { repo.getAllPlaces() }
            val lang = LanguageHelper.current()
            // Keep one record per saved place. The dropdown label and selected
            // name are reordered per the script actually typed into this field.
            placeSuggestions = all.map { place ->
                val names = place.names.mapNotNull { (code, value) ->
                    value.trim().takeIf { it.isNotBlank() }?.let { code to it }
                }.toMap()
                val fallback = place.displayName(lang)
                val initial = suggestionForLanguage(
                    PlaceSuggestion(
                        place.id,
                        names,
                        fallback,
                        fallback,
                        fallback,
                        names.values.distinct()
                    ),
                    lang
                )
                initial
            }.distinctBy { it.placeId }
                .sortedBy { it.label.lowercase() }

            findViewById<AutoCompleteTextView>(R.id.etFromText)?.let(::attachPlaceSuggestions)
            findViewById<AutoCompleteTextView>(R.id.etToText)?.let(::attachPlaceSuggestions)
            (newRows.map { it.name } + existingRows.mapNotNull { it.nameEdit }).forEach { field ->
                if (field is AutoCompleteTextView) attachPlaceSuggestions(field)
            }
        }
    }

    private lateinit var repo: AppRepository
    private lateinit var tvStart: TextView
    private lateinit var stopsBox: LinearLayout
    private lateinit var newStopsBox: LinearLayout
    private lateinit var btnSubmit: Button
    private var isNew = false
    private var routes: List<Route> = emptyList()
    private var selected: Route? = null
    private var startTime: String? = null
    private val existingRows = ArrayList<ExistingStop>()
    private val newRows = ArrayList<NewStop>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_route)
        repo = AppRepository.getInstance(this)
        refreshPlaceSuggestions()
        findViewById<Button>(R.id.btnBackAdd).visibility = View.GONE
        tvStart = findViewById(R.id.tvStartTime)
        stopsBox = findViewById(R.id.stopsBox)
        newStopsBox = findViewById(R.id.newStopsBox)
        btnSubmit = findViewById(R.id.btnSubmitRoute)
        val spinner = findViewById<Spinner>(R.id.spRoute)
        val toggle = findViewById<MaterialButtonToggleGroup>(R.id.toggleMode)

        toggle.addOnButtonCheckedListener { _, checkedId, checked ->
            if (checked) setMode(checkedId == R.id.btnModeNew)
        }
        // Default to a new route; users can switch to copying an existing route's
        // endpoints and stops, then submit the edited details for admin review.
        // Preserve raw user-entered place names in Hindi, Punjabi, or English.
        toggle.visibility = View.GONE
        setMode(true)

        findViewById<Button>(R.id.btnAddNewStop).setOnClickListener { addNewStop() }
        findViewById<Button>(R.id.btnUseExistingRoute).setOnClickListener { setMode(false) }
        findViewById<Button>(R.id.btnUseNewRoute).setOnClickListener { setMode(true) }
        tvStart.setOnClickListener {
            pickTime(startTime) { t ->
                if (t == null || fitsOrder(replaceStart = t)) { startTime = t; showTime(tvStart, t) }
            }
        }
        btnSubmit.setOnClickListener { submit() }

        lifecycleScope.launch {
            routes = repo.getAllRoutes()
            val ids = routes.flatMap { listOf(it.fromId, it.toId) }.toSet()
            val names = repo.getPlacesById(ids)
            val lang = LanguageHelper.current()
            val labels = routes.map { route ->
                val endpoints = "${names[route.fromId]?.displayName(lang) ?: route.fromId} → ${names[route.toId]?.displayName(lang) ?: route.toId}"
                if (route.time.isNotBlank()) "$endpoints • ${route.time}" else endpoints
            }
            spinner.adapter = ArrayAdapter(this@AddRouteActivity, android.R.layout.simple_spinner_dropdown_item, labels)
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position in routes.indices) {
                        val route = routes[position]
                        findViewById<EditText>(R.id.etFromText).setText(names[route.fromId]?.displayName(lang) ?: route.fromId)
                        findViewById<EditText>(R.id.etToText).setText(names[route.toId]?.displayName(lang) ?: route.toId)
                        loadStops(route)
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
            // कोई रूट बना ही नहीं है तो सीधे New Route खुले
            if (routes.isEmpty()) toggle.check(R.id.btnModeNew)
        }
    }

    private fun setMode(newMode: Boolean) {
        isNew = newMode
        // Keep one shared From/To editor on screen in both modes. Existing-route
        // selection only changes which stop rows are loaded into the editor.
        findViewById<View>(R.id.layoutExisting).visibility = if (newMode) View.GONE else View.VISIBLE
        findViewById<View>(R.id.layoutExistingStops).visibility = if (newMode) View.GONE else View.VISIBLE
        findViewById<View>(R.id.layoutNew).visibility = View.VISIBLE
        findViewById<View>(R.id.layoutNewStops).visibility = if (newMode) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnUseExistingRoute).visibility = if (newMode) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnUseNewRoute).visibility = if (newMode) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.tvModeHint).setText(if (newMode) R.string.ar_new_route_intro else R.string.ar_existing_route_intro)
    }

    private fun loadStops(route: Route) {
        selected = route
        lifecycleScope.launch {
            val stops = repo.getRouteStops(route.id)
            val names = repo.getPlacesById(stops.map { it.placeId }.toSet())
            val lang = LanguageHelper.current()
            stopsBox.removeAllViews()
            existingRows.clear()
            val pad = (8 * resources.displayMetrics.density).toInt()
            stops.forEachIndexed { index, stop ->
                val line = LinearLayout(this@AddRouteActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(if (index == 0) 0 else pad, pad / 2, 0, pad / 2)
                }
                val name = placeAutoComplete(getString(R.string.ar_custom_stop_name)).apply {
                    setSingleLine(true)
                    textSize = 16f
                    setText(names[stop.placeId]?.displayName(lang) ?: stop.placeId)
                    filters = arrayOf(android.text.InputFilter.LengthFilter(80))
                    setBackgroundResource(R.drawable.bg_input)
                    setPadding(pad * 2, pad, pad * 2, pad)
                }
                val controls = LinearLayout(this@AddRouteActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val time = timeBox()
                val row = ExistingStop(stop.placeId, time, time = stop.time, nameEdit = name, line = line)
                showTime(time, row.time)
                time.setOnClickListener {
                    pickTime(row.time) { t ->
                        if (t == null || fitsOrder(existingRow = row, existingTime = t)) {
                            row.time = t
                            showTime(time, t)
                        }
                    }
                }
                val remove = Button(this@AddRouteActivity).apply {
                    text = "−"
                    setOnClickListener {
                        if (existingRows.size <= 2) {
                            Toast.makeText(this@AddRouteActivity, R.string.ar_min_stops, Toast.LENGTH_SHORT).show()
                        } else {
                            existingRows.remove(row)
                            stopsBox.removeView(line)
                        }
                    }
                }
                line.addView(name)
                controls.addView(time, LinearLayout.LayoutParams(0, -2, 1f))
                controls.addView(plusButton { insertStopAfter(line, row) })
                controls.addView(remove)
                line.addView(controls)
                stopsBox.addView(line)
                existingRows.add(row)
            }
            startTime = route.time
            showTime(tvStart, startTime)
        }
    }

    private fun plusButton(onClick: () -> Unit): Button = Button(this).apply {
        text = "+"
        textSize = 18f
        minWidth = 0; minimumWidth = 0
        setOnClickListener { onClick() }
    }

    /** मौजूदा रूट के दो पड़ावों के बीच नया पड़ाव (नाम किसी भी भाषा में + समय) */
    private fun insertStopAfter(anchorLine: View, anchorRow: ExistingStop?) {
        val pad = (8 * resources.displayMetrics.density).toInt()
        val line = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad * 2, pad, 0, pad) }
        val name = placeAutoComplete(getString(R.string.ar_custom_stop_name)).apply {
            setSingleLine(true)
            textSize = 16f
            filters = arrayOf(android.text.InputFilter.LengthFilter(80))
            setBackgroundResource(R.drawable.bg_input)
            setPadding(pad * 2, pad, pad * 2, pad)
        }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, pad, 0, 0) }
        val time = timeBox()
        val row = ExistingStop(null, time, nameEdit = name, line = line)
        time.setOnClickListener {
            pickTime(row.time) { t ->
                if (t == null || fitsOrder(existingRow = row, existingTime = t)) { row.time = t; showTime(time, t) }
            }
        }
        val remove = Button(this).apply { text = "−"; setOnClickListener { existingRows.remove(row); stopsBox.removeView(line) } }
        val plus = plusButton { insertStopAfter(line, row) }
        controls.addView(time, LinearLayout.LayoutParams(0, -2, 1f)); controls.addView(plus); controls.addView(remove)
        line.addView(name); line.addView(controls)
        val viewIndex = stopsBox.indexOfChild(anchorLine) + 1
        stopsBox.addView(line, viewIndex)
        val rowIndex = if (anchorRow == null) 0 else existingRows.indexOf(anchorRow) + 1
        existingRows.add(rowIndex, row)
    }

    private fun addNewStop() {
        val pad = (8 * resources.displayMetrics.density).toInt()
        val line = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, pad, 0, pad) }
        val name = placeAutoComplete(getString(R.string.ar_custom_stop_name)).apply {
            setSingleLine(true)
            textSize = 16f
            maxLines = 1
            filters = arrayOf(android.text.InputFilter.LengthFilter(80))
            setBackgroundResource(R.drawable.bg_input)
            setPadding(pad * 2, pad, pad * 2, pad)
        }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, pad, 0, 0) }
        val time = timeBox()
        val remove = Button(this).apply { text = "−"; setOnClickListener { val row = newRows.find { it.name === name }; if (row != null) newRows.remove(row); newStopsBox.removeView(line) } }
        val row = NewStop(name, time)
        time.setOnClickListener {
            pickTime(row.time) { t ->
                if (t == null || fitsOrder(newRow = row, newTime = t)) { row.time = t; showTime(time, t) }
            }
        }
        controls.addView(time, LinearLayout.LayoutParams(0, -2, 1f)); controls.addView(remove)
        line.addView(name); line.addView(controls); newStopsBox.addView(line); newRows.add(row)
    }

    private fun timeBox(): TextView {
        val pad = (10 * resources.displayMetrics.density).toInt()
        return TextView(this).apply {
            text = getString(R.string.ar_tap_time)
            textSize = 15f
            setTextColor(getColor(R.color.textPrimary))
            setBackgroundResource(R.drawable.bg_input)
            setPadding(pad, pad, pad, pad)
        }
    }

    private fun showTime(view: TextView, time: String?) { view.text = if (time == null) getString(R.string.ar_tap_time) else TimeFormat.format(time) }

    private fun pickTime(current: String?, onPicked: (String?) -> Unit) {
        val h = current?.substringBefore(":")?.toIntOrNull() ?: 8
        val m = current?.substringAfter(":", "")?.toIntOrNull() ?: 0
        val dialog = TimePickerDialog(this, { _, hh, mm -> onPicked("%02d:%02d".format(hh, mm)) }, h, m, false)
        dialog.setButton(android.content.DialogInterface.BUTTON_NEUTRAL, getString(R.string.ar_clear)) { _, _ -> onPicked(null) }
        dialog.show()
    }

    /** अभी भरे सारे समय क्रम में: चलने का समय, फिर पड़ाव (खाली समय छूट जाते हैं) */
    private fun orderedTimes(
        replaceStart: String? = null,
        existingRow: ExistingStop? = null, existingTime: String? = null,
        newRow: NewStop? = null, newTime: String? = null
    ): List<String?> {
        val out = ArrayList<String?>()
        out.add(replaceStart ?: startTime)
        if (isNew) newRows.forEach { out.add(if (it === newRow) newTime else it.time) }
        else existingRows.forEach { out.add(if (it === existingRow) existingTime else it.time) }
        return out
    }

    /** समय का क्रम TimeFlow के नियम से सही है या नहीं (गलत हो तो संदेश दिखाता है) */
    private fun fitsOrder(
        replaceStart: String? = null,
        existingRow: ExistingStop? = null, existingTime: String? = null,
        newRow: NewStop? = null, newTime: String? = null
    ): Boolean {
        val ok = TimeFlow.resolve(orderedTimes(replaceStart, existingRow, existingTime, newRow, newTime)).badIndex < 0
        if (!ok) Toast.makeText(this, R.string.ar_time_order, Toast.LENGTH_LONG).show()
        return ok
    }

    private fun submit() {
        if (!SupabaseClient.isConfigured()) { Toast.makeText(this, R.string.ar_not_set, Toast.LENGTH_LONG).show(); return }
        val fromText = findViewById<EditText>(R.id.etFromText).text.toString().trim()
        val toText = findViewById<EditText>(R.id.etToText).text.toString().trim()
        val route = selected
        if (fromText.isBlank() || toText.isBlank()) { Toast.makeText(this, R.string.ar_custom_need_endpoints, Toast.LENGTH_SHORT).show(); return }
        if (!isNew && route == null) { Toast.makeText(this, R.string.ar_pick_route, Toast.LENGTH_SHORT).show(); return }
        val phone = findViewById<EditText>(R.id.etPhone).text.toString().filter { it.isDigit() }
        if (phone.length !in 10..13) { Toast.makeText(this, R.string.ar_need_phone, Toast.LENGTH_SHORT).show(); return }
        val service = findViewById<EditText>(R.id.etService).text.toString().trim()

        val stops = JSONArray()
        if (isNew) {
            for (row in newRows) {
                val name = row.name.text.toString().trim()
                if (name.isNotBlank()) {
                    stops.put(JSONObject().put("name", name).put("time", row.time ?: JSONObject.NULL))
                }
            }
        } else {
            for (row in existingRows) {
                val nm = row.nameEdit?.text?.toString()?.trim().orEmpty()
                if (nm.isBlank()) {
                    Toast.makeText(this, R.string.ar_stop_name_required, Toast.LENGTH_SHORT).show()
                    return
                }
                stops.put(JSONObject()
                    .put("place", row.placeId ?: JSONObject.NULL)
                    .put("name", nm)
                    .put("time", row.time ?: JSONObject.NULL))
            }
        }
        val anyTime = !startTime.isNullOrBlank() ||
            if (isNew) newRows.any { !it.time.isNullOrBlank() } else existingRows.any { !it.time.isNullOrBlank() }
        if (!anyTime) {
            Toast.makeText(this, R.string.ar_need_time, Toast.LENGTH_LONG).show()
            return
        }
        // Validate order among only real, non-empty times; blank stop times are optional.
        if (TimeFlow.resolve(orderedTimes().filterNotNull().filter { it.isNotBlank() }).badIndex >= 0) {
            Toast.makeText(this, R.string.ar_time_order, Toast.LENGTH_LONG).show()
            return
        }

        val body = JSONObject()
            .put("submission_type", "new_route")
            .put("from_id", JSONObject.NULL)
            .put("to_id", JSONObject.NULL)
            .put("from_text", fromText)
            .put("to_text", toText)
            .put("route_text", "$fromText → $toText")
            .put("start_time", startTime ?: JSONObject.NULL)
            .put("service_name", if (service.isBlank()) JSONObject.NULL else service)
            .put("stops", stops)
            .put("phone", phone)
        btnSubmit.isEnabled = false
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { SupabaseClient.insertRouteSubmission(body) }
            if (ok) { Toast.makeText(this@AddRouteActivity, R.string.ar_sent, Toast.LENGTH_LONG).show(); finish() }
            else {
                btnSubmit.isEnabled = true
                val detail = SupabaseClient.lastError.ifBlank { getString(R.string.ar_failed) }
                Toast.makeText(this@AddRouteActivity, "${getString(R.string.ar_failed)}\n$detail", Toast.LENGTH_LONG).show()
            }
        }
    }
}
