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
            // Show only the name matching the script currently being typed.
            label = selected
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
                    .distinctBy { it.label.trim().lowercase().replace(Regex("\\s+"), " ") }
                    .sortedBy { it.label.lowercase() }
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
        // Keep the selected Place ID with the field. Any subsequent manual edit
        // invalidates the selection so typed text cannot accidentally reuse an old ID.
        if (field.tag != true) {
            field.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    field.setTag(R.id.etFromText, false)
                    field.setTag(R.id.etToText, null)
                }
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
            field.tag = true
        }
        field.setOnItemClickListener { parent, _, position, _ ->
            val suggestion = parent.getItemAtPosition(position) as? PlaceSuggestion
            if (suggestion != null) {
                field.setText(suggestion.selectedName, false)
                field.setSelection(field.text.length)
                field.setTag(R.id.etFromText, true)
                field.setTag(R.id.etToText, suggestion.placeId)
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
            val routeUsage = withContext(Dispatchers.IO) {
                repo.getAllRoutes().flatMap { route -> listOf(route.fromId, route.toId) }
                    .groupingBy { it }.eachCount()
            }
            placeSuggestions = all.map { place ->
                val names = place.names.mapNotNull { (code, value) ->
                    value.trim().takeIf { it.isNotBlank() }?.let { code to it }
                }.toMap()
                val fallback = place.displayName(lang)
                suggestionForLanguage(
                    PlaceSuggestion(
                        place.id,
                        names,
                        fallback,
                        fallback,
                        fallback,
                        (names.values + place.searchText).distinct()
                    ),
                    lang
                )
            }
                // Exact duplicate multilingual records are shown once. Prefer the ID
                // referenced by the most routes; distinct names such as "Rampur RJ"
                // and "Rampur PB" remain separate Places.
                .sortedWith(compareByDescending<PlaceSuggestion> { routeUsage[it.placeId] ?: 0 }
                    .thenBy { it.label.lowercase() })
                .distinctBy { item ->
                    // Duplicate visible names should not be repeated in suggestions.
                    // The admin can disambiguate genuinely different places with a suffix.
                    item.label.trim().lowercase().replace(Regex("\\s+"), " ")
                }

            findViewById<AutoCompleteTextView>(R.id.etFromText)?.let(::attachPlaceSuggestions)
            findViewById<AutoCompleteTextView>(R.id.etToText)?.let(::attachPlaceSuggestions)
            (newRows.map { it.name } + existingRows.mapNotNull { it.nameEdit }).forEach { field ->
                if (field is AutoCompleteTextView) attachPlaceSuggestions(field)
            }
        }
    }

    private lateinit var repo: AppRepository
    private lateinit var tvStart: TextView
    private lateinit var tvEndTime: TextView
    private lateinit var stopsBox: LinearLayout
    private lateinit var newStopsBox: LinearLayout
    private lateinit var btnSubmit: Button
    private var isNew = false
    private var routes: List<Route> = emptyList()
    private var selected: Route? = null
    private var startTime: String? = null
    private var endTime: String? = null
    private val existingRows = ArrayList<ExistingStop>()
    private val newRows = ArrayList<NewStop>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_route)
        repo = AppRepository.getInstance(this)
        refreshPlaceSuggestions()
        tvStart = findViewById(R.id.tvStartTime)
        tvEndTime = findViewById(R.id.tvEndTime)
        stopsBox = findViewById(R.id.stopsBox)
        newStopsBox = findViewById(R.id.newStopsBox)
        btnSubmit = findViewById(R.id.btnSubmitRoute)
        val spinner = findViewById<Spinner>(R.id.spRoute)
        // Default to a new route; users can switch to copying an existing route's
        // endpoints and stops, then submit the edited details for admin review.
        // Preserve raw user-entered place names in Hindi, Punjabi, or English.
        setMode(true)

        findViewById<Button>(R.id.btnAddNewStop).setOnClickListener { addNewStop() }
        findViewById<Button>(R.id.btnUseExistingRoute).setOnClickListener {
            setMode(false)
            loadSelectedRoute()
        }
        findViewById<Button>(R.id.btnUseNewRoute).setOnClickListener { setMode(true) }
        tvStart.setOnClickListener {
            pickTime(startTime) { t ->
                if (t == null || fitsOrder(replaceStart = t)) { startTime = t; showTime(tvStart, t) }
            }
        }
        tvEndTime.setOnClickListener {
            pickTime(endTime) { t ->
                if (t == null || fitsOrder(replaceEnd = t)) { endTime = t; showTime(tvEndTime, t) }
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
                    if (!isNew && position in routes.indices) loadSelectedRoute()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
            // कोई रूट बना ही नहीं है तो सीधे New Route खुले
        }
    }

    private fun setMode(newMode: Boolean) {
        val wasNew = isNew
        isNew = newMode
        if (newMode && !wasNew) {
            // getString(R.string.ar_create_new) must be a clean draft, not a leftover copy of the route.
            findViewById<EditText>(R.id.etFromText).text.clear()
            findViewById<EditText>(R.id.etToText).text.clear()
            startTime = null
            endTime = null
            selected = null
            existingRows.clear()
            newRows.clear()
            stopsBox.removeAllViews()
            newStopsBox.removeAllViews()
            showTime(tvStart, null)
            showTime(tvEndTime, null)
        }
        tvEndTime.visibility = if (newMode) View.VISIBLE else View.GONE
        // Keep one shared From/To editor on screen in both modes.
        findViewById<View>(R.id.layoutExisting).visibility = if (newMode) View.GONE else View.VISIBLE
        // Both modes use the same stop editor and row layout. Only route selection differs.
        findViewById<View>(R.id.layoutExistingStops).visibility = View.VISIBLE
        findViewById<View>(R.id.layoutNew).visibility = View.VISIBLE
        findViewById<View>(R.id.layoutNewStops).visibility = View.GONE
        findViewById<View>(R.id.btnUseExistingRoute).visibility = if (newMode) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnUseNewRoute).visibility = if (newMode) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.tvModeHint).setText(if (newMode) R.string.ar_new_route_intro else R.string.ar_existing_route_intro)
    }

    private fun loadSelectedRoute() {
        val spinner = findViewById<Spinner>(R.id.spRoute)
        val route = routes.getOrNull(spinner.selectedItemPosition) ?: return
        lifecycleScope.launch {
            val names = repo.getPlacesById(setOf(route.fromId, route.toId))
            val lang = LanguageHelper.current()
            findViewById<EditText>(R.id.etFromText).apply {
                setText(names[route.fromId]?.displayName(lang) ?: route.fromId)
                setTag(R.id.etFromText, true)
                setTag(R.id.etToText, route.fromId)
            }
            findViewById<EditText>(R.id.etToText).apply {
                setText(names[route.toId]?.displayName(lang) ?: route.toId)
                setTag(R.id.etFromText, true)
                setTag(R.id.etToText, route.toId)
            }
            loadStops(route)
        }
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
                    setTag(R.id.etFromText, true)
                    setTag(R.id.etToText, stop.placeId)
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
                val number = rowNumber(index, stops.size)
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
                controls.addView(number, LinearLayout.LayoutParams((30 * resources.displayMetrics.density).toInt(), (28 * resources.displayMetrics.density).toInt()).apply { marginEnd = (5 * resources.displayMetrics.density).toInt() })
                name.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                name.textSize = 14f
                name.setPadding((5 * resources.displayMetrics.density).toInt(), 0, (3 * resources.displayMetrics.density).toInt(), 0)
                controls.addView(name, LinearLayout.LayoutParams(0, (44 * resources.displayMetrics.density).toInt(), 1f))
                controls.addView(time, LinearLayout.LayoutParams((56 * resources.displayMetrics.density).toInt(), (36 * resources.displayMetrics.density).toInt()).apply { marginStart = (3 * resources.displayMetrics.density).toInt() })
                controls.addView(plusButton { insertStopAfter(line, row) }, LinearLayout.LayoutParams((34 * resources.displayMetrics.density).toInt(), (36 * resources.displayMetrics.density).toInt()).apply { marginStart = (4 * resources.displayMetrics.density).toInt() })
                controls.addView(moreButton(row), LinearLayout.LayoutParams((28 * resources.displayMetrics.density).toInt(), (40 * resources.displayMetrics.density).toInt()).apply { marginStart = (1 * resources.displayMetrics.density).toInt() })
                line.addView(controls)
                line.background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.TRANSPARENT)
                    cornerRadius = (7 * resources.displayMetrics.density)
                }
                stopsBox.addView(line)
                existingRows.add(row)
            }
            refreshStopRowNumbers()
            startTime = route.time
            endTime = stops.lastOrNull { it.placeId == route.toId }?.time
            showTime(tvStart, startTime)
            showTime(tvEndTime, endTime)
        }
    }

    private fun plusButton(onClick: () -> Unit): TextView = TextView(this).apply {
        text = "+"
        textSize = 23f
        gravity = Gravity.CENTER
        setTextColor(android.graphics.Color.WHITE)
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.rgb(32, 145, 75))
            cornerRadius = (7 * resources.displayMetrics.density)
        }
        contentDescription = getString(R.string.ar_add_stop_below)
        setOnClickListener { onClick() }
    }

    private fun rowNumber(index: Int, total: Int): TextView = TextView(this).apply {
        text = (index + 1).toString()
        textSize = 13f
        gravity = Gravity.CENTER
        setTextColor(android.graphics.Color.WHITE)
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(if (index == 0 || index == total - 1) android.graphics.Color.rgb(44, 115, 83) else android.graphics.Color.rgb(83, 91, 108))
            cornerRadius = (14 * resources.displayMetrics.density)
        }
        minWidth = (28 * resources.displayMetrics.density).toInt()
        setPadding(5, 3, 5, 3)
    }

    private fun moreButton(row: ExistingStop): TextView = TextView(this).apply {
        text = "⋮"
        textSize = 23f
        gravity = Gravity.CENTER
        setTextColor(getColor(R.color.textPrimary))
        contentDescription = getString(R.string.ar_more_stop_actions)
        setOnClickListener { anchor ->
            val index = existingRows.indexOf(row)
            val menu = android.widget.PopupMenu(this@AddRouteActivity, anchor)
            menu.menu.add(getString(R.string.ar_move_up)).isEnabled = index > 0
            menu.menu.add(getString(R.string.ar_move_down)).isEnabled = index in 0 until existingRows.lastIndex
            menu.menu.add(getString(R.string.ar_remove_stop))
            menu.setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "Move up" -> if (index > 0) {
                        val current = existingRows.removeAt(index); existingRows.add(index - 1, current)
                        reorderStopViews()
                    }
                    "Move down" -> if (index < existingRows.lastIndex) {
                        val current = existingRows.removeAt(index); existingRows.add(index + 1, current)
                        reorderStopViews()
                    }
                    "Remove stop" -> if (existingRows.size > 2) {
                        existingRows.remove(row); row.line?.let { stopsBox.removeView(it) }; refreshStopRowNumbers()
                    } else Toast.makeText(this@AddRouteActivity, R.string.ar_min_stops, Toast.LENGTH_SHORT).show()
                }
                true
            }
            menu.show()
        }
    }

    private fun reorderStopViews() {
        stopsBox.removeAllViews()
        existingRows.forEach { row -> row.line?.let { stopsBox.addView(it) } }
        refreshStopRowNumbers()
    }

    private fun refreshStopRowNumbers() {
        existingRows.forEachIndexed { index, row ->
            val line = row.line as? LinearLayout ?: return@forEachIndexed
            val controls = line.getChildAt(0) as? LinearLayout ?: return@forEachIndexed
            val number = controls.getChildAt(0) as? TextView
            number?.text = (index + 1).toString()
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (index == 0 || index == existingRows.lastIndex) android.graphics.Color.rgb(44, 115, 83) else android.graphics.Color.rgb(83, 91, 108))
                cornerRadius = (14 * resources.displayMetrics.density)
            }
            number?.background = drawable
        }
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
        controls.addView(rowNumber(existingRows.indexOf(anchorRow).coerceAtLeast(0) + 1, existingRows.size + 1), LinearLayout.LayoutParams((30 * resources.displayMetrics.density).toInt(), (28 * resources.displayMetrics.density).toInt()).apply { marginEnd = (5 * resources.displayMetrics.density).toInt() })
        name.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        name.textSize = 14f
        name.setPadding((5 * resources.displayMetrics.density).toInt(), 0, (3 * resources.displayMetrics.density).toInt(), 0)
        controls.addView(name, LinearLayout.LayoutParams(0, (44 * resources.displayMetrics.density).toInt(), 1f))
        controls.addView(time, LinearLayout.LayoutParams((56 * resources.displayMetrics.density).toInt(), (36 * resources.displayMetrics.density).toInt()).apply { marginStart = (3 * resources.displayMetrics.density).toInt() })
        controls.addView(plusButton { insertStopAfter(line, row) }, LinearLayout.LayoutParams((34 * resources.displayMetrics.density).toInt(), (36 * resources.displayMetrics.density).toInt()).apply { marginStart = (4 * resources.displayMetrics.density).toInt() })
        controls.addView(moreButton(row), LinearLayout.LayoutParams((28 * resources.displayMetrics.density).toInt(), (40 * resources.displayMetrics.density).toInt()).apply { marginStart = (1 * resources.displayMetrics.density).toInt() })
        line.addView(controls)
        val viewIndex = stopsBox.indexOfChild(anchorLine) + 1
        stopsBox.addView(line, viewIndex)
        val rowIndex = if (anchorRow == null) 0 else existingRows.indexOf(anchorRow) + 1
        existingRows.add(rowIndex, row)
        refreshStopRowNumbers()
    }

    private fun addNewStop() {
        // New routes use the exact same stop row editor as existing-route details.
        val last = existingRows.lastOrNull()
        if (last != null && last.line != null) {
            insertStopAfter(last.line!!, last)
            return
        }
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
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val time = timeBox()
        val row = ExistingStop(null, time, nameEdit = name, line = line)
        time.setOnClickListener {
            pickTime(row.time) { t ->
                if (t == null || fitsOrder(existingRow = row, existingTime = t)) { row.time = t; showTime(time, t) }
            }
        }
        val remove = Button(this).apply { text = "−"; setOnClickListener { existingRows.remove(row); stopsBox.removeView(line) } }
        controls.addView(rowNumber(0, 2), LinearLayout.LayoutParams((30 * resources.displayMetrics.density).toInt(), (28 * resources.displayMetrics.density).toInt()).apply { marginEnd = (5 * resources.displayMetrics.density).toInt() })
        name.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        name.textSize = 14f
        name.setPadding((5 * resources.displayMetrics.density).toInt(), 0, (3 * resources.displayMetrics.density).toInt(), 0)
        controls.addView(name, LinearLayout.LayoutParams(0, (44 * resources.displayMetrics.density).toInt(), 1f))
        controls.addView(time, LinearLayout.LayoutParams((56 * resources.displayMetrics.density).toInt(), (36 * resources.displayMetrics.density).toInt()).apply { marginStart = (3 * resources.displayMetrics.density).toInt() })
        controls.addView(plusButton { insertStopAfter(line, row) }, LinearLayout.LayoutParams((34 * resources.displayMetrics.density).toInt(), (36 * resources.displayMetrics.density).toInt()).apply { marginStart = (4 * resources.displayMetrics.density).toInt() })
        controls.addView(moreButton(row), LinearLayout.LayoutParams((28 * resources.displayMetrics.density).toInt(), (40 * resources.displayMetrics.density).toInt()).apply { marginStart = (1 * resources.displayMetrics.density).toInt() })
        line.addView(controls)
        stopsBox.addView(line); existingRows.add(row)
        refreshStopRowNumbers()
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
        newRow: NewStop? = null, newTime: String? = null,
        replaceEnd: String? = null
    ): List<String?> {
        val out = ArrayList<String?>()
        out.add(replaceStart ?: startTime)
        existingRows.forEach { out.add(if (it === existingRow) existingTime else it.time) }
        if (isNew) out.add(replaceEnd ?: endTime)
        return out
    }

    /** समय का क्रम TimeFlow के नियम से सही है या नहीं (गलत हो तो संदेश दिखाता है) */
    private fun fitsOrder(
        replaceStart: String? = null,
        existingRow: ExistingStop? = null, existingTime: String? = null,
        newRow: NewStop? = null, newTime: String? = null,
        replaceEnd: String? = null
    ): Boolean {
        val ok = TimeFlow.resolve(orderedTimes(replaceStart, existingRow, existingTime, newRow, newTime, replaceEnd)).badIndex < 0
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
        for (row in existingRows) {
            val nm = row.nameEdit?.text?.toString()?.trim().orEmpty()
            if (nm.isBlank()) {
                Toast.makeText(this, R.string.ar_stop_name_required, Toast.LENGTH_SHORT).show()
                return
            }
            stops.put(JSONObject()
                .put("place", (row.nameEdit?.getTag(R.id.etToText) as? String) ?: JSONObject.NULL)
                .put("name", nm)
                .put("time", row.time ?: JSONObject.NULL))
        }
        // New routes store the destination as the final stop so its timing is reviewed and published too.
        if (isNew) {
            stops.put(JSONObject()
                .put("place", (findViewById<EditText>(R.id.etToText).getTag(R.id.etToText) as? String) ?: JSONObject.NULL)
                .put("name", toText)
                .put("time", endTime ?: JSONObject.NULL))
        }
        val anyTime = !startTime.isNullOrBlank() || !endTime.isNullOrBlank() || existingRows.any { !it.time.isNullOrBlank() }
        if (!anyTime) {
            Toast.makeText(this, R.string.ar_need_time, Toast.LENGTH_LONG).show()
            return
        }
        // At least one time is mandatory; all other stop times may remain blank.
        if (TimeFlow.resolve(orderedTimes().filterNotNull().filter { it.isNotBlank() }).badIndex >= 0) {
            Toast.makeText(this, R.string.ar_time_order, Toast.LENGTH_LONG).show()
            return
        }

        val body = JSONObject()
            .put("submission_type", "new_route")
            .put("from_id", (findViewById<EditText>(R.id.etFromText).getTag(R.id.etToText) as? String)?.let { it } ?: JSONObject.NULL)
            .put("to_id", (findViewById<EditText>(R.id.etToText).getTag(R.id.etToText) as? String)?.let { it } ?: JSONObject.NULL)
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
