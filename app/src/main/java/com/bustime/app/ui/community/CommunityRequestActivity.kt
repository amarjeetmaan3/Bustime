package com.bustime.app.ui.community

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bustime.app.data.repository.AppRepository
import com.bustime.app.models.Route
import com.bustime.app.models.RouteStop
import com.bustime.app.models.Place
import com.bustime.app.utils.LanguageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

/** Independent, append-only community request forms. Each correction is a separate database row. */
class CommunityRequestActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private val fields = linkedMapOf<String, EditText>()
    private val spinners = linkedMapOf<String, Spinner>()
    private var routes: List<Route> = emptyList()
    private var routeStops: List<RouteStop> = emptyList()
    private var placeNames: Map<String, String> = emptyMap()
    private var allPlaces: List<Place> = emptyList()
    private var routeSpinner: Spinner? = null
    private var stopOne: Spinner? = null
    private var stopTwo: Spinner? = null
    private var correctionType: Spinner? = null
    private var missingExisting: Spinner? = null
    private var correctedStop: Spinner? = null
    private var missingCheck: CheckBox? = null
    private var timeCheck: CheckBox? = null
    private var longRouteName = ""
    private var missingSection: LinearLayout? = null
    private var timeSection: LinearLayout? = null
    private var missingPosition: Spinner? = null
    private val missingPlaceFields = mutableListOf<AutoCompleteTextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: "feedback"
        title = when (mode) { "correction" -> "Route Correction"; "vehicle" -> "Add Auto / Taxi"; "bus_operator" -> "Add Your Bus"; "vehicle_correction" -> if (intent.getStringExtra("vehicle_type") == "taxi") "Taxi Correction" else "Auto Correction"; else -> "Feedback" }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(24)) }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        text("${title}\n${getString(com.bustime.app.R.string.community_review_notice)}", 18f)
        when (mode) {
            "correction" -> loadCorrectionForm()
            "vehicle" -> vehicleForm()
            "bus_operator" -> busOperatorForm()
            "vehicle_correction" -> vehicleCorrectionForm()
            else -> feedbackForm()
        }
    }

    private fun loadCorrectionForm() {
        if (!CommunitySupabase.isConfigured()) { text(getString(com.bustime.app.R.string.community_online_not_configured), 14f); return }
        lifecycleScope.launch {
            routes = AppRepository.getInstance(this@CommunityRequestActivity).getAllRoutes()
            if (routes.isEmpty()) { text(getString(com.bustime.app.R.string.community_no_routes), 14f); return@launch }
            val repo = AppRepository.getInstance(this@CommunityRequestActivity)
            allPlaces = repo.getAllPlaces()
            placeNames = allPlaces.associate { it.id to it.displayName(LanguageHelper.current()) }
            routeSpinner = spinner(getString(com.bustime.app.R.string.community_choose_route), routes.map { routeLabel(it) })
            val requestedRouteId = intent.getStringExtra("route_id")
            routeSpinner?.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    lifecycleScope.launch { loadStopsFor(routes[position]) }
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
            if (requestedRouteId != null) {
                val requestedIndex = routes.indexOfFirst { it.id == requestedRouteId }
                if (requestedIndex >= 0) routeSpinner?.setSelection(requestedIndex)
            }
            heading(getString(com.bustime.app.R.string.community_what_correction))
            missingCheck = CheckBox(this@CommunityRequestActivity).apply { text = getString(com.bustime.app.R.string.community_missing_stop); isChecked = false }
            timeCheck = CheckBox(this@CommunityRequestActivity).apply { text = getString(com.bustime.app.R.string.community_correct_time) }
            root.addView(missingCheck); root.addView(timeCheck)
            missingSection = LinearLayout(this@CommunityRequestActivity).apply { orientation = LinearLayout.VERTICAL }
            root.addView(missingSection!!)
            headingIn(missingSection!!, "Where are stops missing?")
            textIn(missingSection!!, "Add missing stops at the beginning, between existing stops, or at the end. For several consecutive stops, enter one stop name per line, in travel order.", 14f)
            missingPosition = spinnerIn(missingSection!!, "Missing stop location", listOf("Before the first listed stop", "Between existing stops", "After the last listed stop"))
            stopOne = spinnerIn(missingSection!!, "Existing stop before the missing stops", emptyList())
            stopTwo = spinnerIn(missingSection!!, "Existing stop after the missing stops", emptyList())
            val missingNamesBox = LinearLayout(this@CommunityRequestActivity).apply { orientation = LinearLayout.VERTICAL }
            missingSection!!.addView(missingNamesBox)
            addMissingPlaceField(missingNamesBox)
            buttonIn(missingSection!!, "+ Add another missing stop") { addMissingPlaceField(missingNamesBox) }
            timePickerFieldIn(missingSection!!, "missing_time", "Time of first missing stop (optional)")
            missingPosition?.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    stopOne?.visibility = if (position == 0) View.GONE else View.VISIBLE
                    stopTwo?.visibility = if (position == 2) View.GONE else View.VISIBLE
                    if (position == 0) stopTwo?.setSelection(0)
                    if (position == 2) stopOne?.setSelection((routeStops.size - 1).coerceAtLeast(0))
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
            timeSection = LinearLayout(this@CommunityRequestActivity).apply { orientation = LinearLayout.VERTICAL }
            root.addView(timeSection!!)
            headingIn(timeSection!!, getString(com.bustime.app.R.string.community_correct_time_details))
            correctedStop = spinnerIn(timeSection!!, getString(com.bustime.app.R.string.community_choose_stop), emptyList())
            timePickerFieldIn(timeSection!!, "corrected_time", getString(com.bustime.app.R.string.community_correct_time_hint))
            field("correction_note", getString(com.bustime.app.R.string.community_extra_details))
            field("phone", "Mobile number (required)")
            val updateSections = {
                missingSection?.visibility = if (missingCheck?.isChecked == true) View.VISIBLE else View.GONE
                timeSection?.visibility = if (timeCheck?.isChecked == true) View.VISIBLE else View.GONE
            }
            missingCheck?.setOnCheckedChangeListener { _, _ -> updateSections() }
            timeCheck?.setOnCheckedChangeListener { _, _ -> updateSections() }
            updateSections()
            button("Send correction request") { submitCorrection() }
        }
    }

    private suspend fun loadStopsFor(route: Route) {
        routeStops = AppRepository.getInstance(this).getRouteStops(route.id)
        val allIds = routeStops.map { it.placeId }.toSet()
        val places = AppRepository.getInstance(this).getPlacesById(allIds)
        placeNames = places.mapValues { it.value.displayName(LanguageHelper.current()) }
        val labels = routeStops.mapIndexed { i, s -> "${i + 1}. ${placeNames[s.placeId] ?: s.placeId}" }
        fun update(target: Spinner?, values: List<String>) {
            target?.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, values)
        }
        update(stopOne, labels)
        update(stopTwo, labels)
        update(correctedStop, labels)
        stopOne?.setSelection(0)
        stopTwo?.setSelection((labels.size - 1).coerceAtLeast(0))

    }

    private fun submitCorrection() {
        val route = routes.getOrNull(routeSpinner?.selectedItemPosition ?: -1) ?: return
        val sendMissing = missingCheck?.isChecked == true
        val sendTime = timeCheck?.isChecked == true
        if (!sendMissing && !sendTime) { toast("Select Missing Stop, Correct Time, or both."); return }
        val note = value("correction_note")
        val phone = digits(value("phone"))
        if (phone.length !in 10..13) { toast("Enter a valid mobile number before submitting a correction."); return }
        val jobs = mutableListOf<Pair<String, JSONObject>>()
        if (sendMissing) {
            val missingNames = missingPlaceFields.map { it.text.toString().trim() }.filter { it.isNotBlank() }
            if (missingNames.isEmpty()) { toast("Enter at least one missing stop name."); return }
            val position = missingPosition?.selectedItemPosition ?: 1
            val before: RouteStop?
            val after: RouteStop?
            when (position) {
                0 -> { before = null; after = routeStops.firstOrNull() }
                2 -> { before = routeStops.lastOrNull(); after = null }
                else -> {
                    before = routeStops.getOrNull(stopOne?.selectedItemPosition ?: -1)
                    after = routeStops.getOrNull(stopTwo?.selectedItemPosition ?: -1)
                    if (before == null || after == null || after.stopOrder <= before.stopOrder) { toast("Choose existing stops in the correct order."); return }
                }
            }
            if (routeStops.isEmpty() || (position == 0 && after == null) || (position == 2 && before == null)) { toast("This route has no existing stops to anchor the correction."); return }
            val firstTime = value("missing_time")
            if (firstTime.isNotBlank() && !firstTime.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$"))) { toast("Enter time as HH:MM."); return }
            val positionLabel = when (position) { 0 -> "before first stop"; 2 -> "after last stop"; else -> "between existing stops" }
            missingNames.forEachIndexed { index, missingName ->
                val noteWithOrder = listOfNotNull(note.takeIf { it.isNotBlank() }, "Insertion position: $positionLabel", "Added stop order: ${index + 1} of ${missingNames.size}").joinToString(" | ")
                jobs += "missing_stop" to JSONObject().put("route_id", route.id).put("request_type", "missing_stop")
                    .put("before_stop_id", before?.placeId ?: JSONObject.NULL).put("after_stop_id", after?.placeId ?: JSONObject.NULL)
                    .put("missing_place_name", missingName).put("missing_place_id", JSONObject.NULL)
                    .put("missing_time", if (index == 0 && firstTime.isNotBlank()) firstTime else JSONObject.NULL)
                    .put("notes", noteWithOrder).put("phone", phone)
            }
        }
        if (sendTime) {
            val stop = routeStops.getOrNull(correctedStop?.selectedItemPosition ?: -1)
            val t = value("corrected_time")
            if (stop == null || !t.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$"))) { toast("Choose a stop and enter the corrected time as HH:MM."); return }
            jobs += "correct_time" to JSONObject().put("route_id", route.id).put("request_type", "correct_time")
                .put("stop_id", stop.placeId).put("corrected_time", t).put("notes", note.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                .put("phone", phone)
        }
        sendRequests(jobs.map { it.second }, "correction_submissions", "Correction request(s) sent for admin review.")
    }

    private fun vehicleForm() {
        val requestedType = intent.getStringExtra("vehicle_type")
        val typeSpinner = spinner("Vehicle type", listOf("Auto", "Taxi"), "vehicle_type")
        if (requestedType == "taxi") typeSpinner.setSelection(1)
        if (requestedType == "auto" || requestedType == "taxi") typeSpinner.visibility = View.GONE
        field("person_name", "Driver name")
        field("vehicle_name", "Vehicle name")
        field("vehicle_model_number", "Vehicle model number")
        field("seats", "Number of seats")
        field("vehicle_number", "Vehicle registration number")
        field("location", "Location address / service area (enter once)")
        field("union_name", "Union name (required; admin will review)")
        field("phone", "Mobile number")
        val fuelSpinner = spinner("Auto fuel type", listOf("EV", "Oil", "CNG", "Other"), "fuel_type")
        if (requestedType != "auto") fuelSpinner.visibility = View.GONE
        field("details", "Other information (optional)").apply { minLines = 2; maxLines = 4; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE }
        button("Submit listing for admin review") {
            val type = if (requestedType == "auto" || (requestedType == null && spinners["vehicle_type"]?.selectedItemPosition == 0)) "auto" else "taxi"
            val name = value("person_name"); val vehicle = value("vehicle_name"); val phone = digits(value("phone"))
            val seats = value("seats").toIntOrNull()
            val area = value("location")
            if (name.isBlank() || vehicle.isBlank() || value("vehicle_model_number").isBlank() || value("vehicle_number").isBlank() ||
                area.isBlank() || value("union_name").isBlank() || phone.length !in 10..13 || seats == null || seats < 1) {
                toast("Complete all required fields, including seat count, vehicle number, service area and union name."); return@button
            }
            val body = JSONObject().put("vehicle_type", type).put("person_name", name).put("vehicle_name", vehicle)
                .put("vehicle_model_number", value("vehicle_model_number")).put("vehicle_number", value("vehicle_number"))
                .put("seats", seats).put("location", area).put("area_name", area)
                .put("union_name", value("union_name")).put("phone", phone)
                .put("fuel_type", if (type == "auto") spinners["fuel_type"]?.selectedItem?.toString()?.lowercase(Locale.ROOT) else JSONObject.NULL)
                .put("details", value("details").takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            sendRequests(listOf(body), "vehicle_submissions", "Listing sent for admin review.")
        }
    }

    private fun busOperatorForm() {
        heading("Add Your Bus")
        text("Enter bus details for admin review. Your listing appears publicly only after approval.", 14f)
        field("service_name", "Service name")
        field("operator_name", "Bus owner name")
        val defaultCategories = listOf("Ordinary", "Luxury", "Sleeper", "Sleeper Plus", "Seater Plus", "Seater", "Sleeper + Seater", "Custom")
        val categorySpinner = spinner("Bus category", defaultCategories, "bus_type")
        lifecycleScope.launch {
            val categories = withContext(Dispatchers.IO) { CommunitySupabase.getBusCategories() }
            if (categories.isNotEmpty()) {
                val selected = categorySpinner.selectedItem?.toString()
                categorySpinner.adapter = ArrayAdapter(this@CommunityRequestActivity, android.R.layout.simple_spinner_dropdown_item, categories)
                val index = categories.indexOf(selected)
                if (index >= 0) categorySpinner.setSelection(index)
            }
        }
        field("custom_category", "Describe your specific bus type / variant (required for every category)").apply { minLines = 2; maxLines = 4; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE }
        field("sleeper_count", "Number of sleeper berths")
        field("seater_count", "Number of seater seats")
        field("phone", "Operator mobile number")
        field("whatsapp", "WhatsApp number (optional)")
        field("details", "Additional information (optional)").apply { minLines = 2; maxLines = 4; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE }
        button("Submit bus for admin review") {
            val operator = value("operator_name")
            val service = value("service_name")
            val phone = digits(value("phone"))
            val category = spinners["bus_type"]?.selectedItem?.toString().orEmpty()
            val variant = value("custom_category")
            val sleeper = value("sleeper_count").toIntOrNull()
            val seater = value("seater_count").toIntOrNull()
            if (service.isBlank() || operator.isBlank() || phone.length !in 10..13 || category.isBlank() || variant.isBlank() ||
                sleeper == null || sleeper < 0 || seater == null || seater < 0 ) {
                toast("Fill every required field, including bus variant, berth/seat counts and mobile number."); return@button
            }
            val body = JSONObject()
                .put("operator_name", operator).put("service_name", service)
                .put("bus_type", category).put("custom_category", variant)
                .put("seat_type", if (sleeper > 0 && seater > 0) "Sleeper + Seater" else if (sleeper > 0) "Sleeper" else if (seater > 0) "Seater" else "Not specified")
                .put("sleeper_count", sleeper).put("seater_count", seater)
                .put("from_text", JSONObject.NULL).put("to_text", JSONObject.NULL)
                .put("phone", phone)
                .put("whatsapp", digits(value("whatsapp")).takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                .put("details", value("details").takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            sendRequests(listOf(body), "bus_operator_submissions", "Bus submitted for admin verification.")
        }
    }

    private fun vehicleCorrectionForm() {
        val type = intent.getStringExtra("vehicle_type")?.takeIf { it == "taxi" || it == "auto" } ?: "auto"
        val driverId = intent.getStringExtra("driver_id").orEmpty()
        val driverName = intent.getStringExtra("driver_name").orEmpty()
        val driverVehicle = intent.getStringExtra("driver_vehicle").orEmpty()
        val driverPhone = intent.getStringExtra("driver_phone").orEmpty()
        val driverSeats = intent.getIntExtra("driver_seats", -1)
        val driverFuel = intent.getStringExtra("driver_fuel").orEmpty()
        val driverInfo = intent.getStringExtra("driver_info").orEmpty()
        text("Correct details for this ${type.uppercase(Locale.ROOT)} listing. Your request will be reviewed by the admin.", 14f)
        text("Driver: $driverName · Vehicle: $driverVehicle", 16f, true)
        field("correction_name", "Correct driver / contact name").setText(driverName)
        field("correction_vehicle", "Correct vehicle name / model").setText(driverVehicle)
        field("correction_phone", "Correct mobile number").setText(driverPhone)
        if (type == "taxi") field("correction_seats", "Correct number of seats").setText(if (driverSeats > 0) driverSeats.toString() else "")
        else field("correction_fuel", "Correct fuel type (EV or Oil)").setText(driverFuel)
        field("correction_info", "Correct other details").setText(driverInfo)
        field("correction_note", "Explain what needs correction")
        button("Send correction request") {
            val name = value("correction_name")
            val vehicle = value("correction_vehicle")
            val phone = digits(value("correction_phone"))
            val detail = value("correction_note")
            if (name.isBlank() || vehicle.isBlank() || phone.length !in 10..13 || detail.length < 4) {
                toast("Enter corrected name, vehicle, valid phone and explain the correction."); return@button
            }
            val details = buildString {
                append("Vehicle correction request\nType: ").append(type)
                append("\nDriver ID: ").append(driverId)
                append("\nCurrent name: ").append(driverName)
                append("\nProposed name: ").append(name)
                append("\nCurrent vehicle: ").append(driverVehicle)
                append("\nProposed vehicle: ").append(vehicle)
                append("\nCurrent phone: ").append(driverPhone)
                append("\nProposed phone: ").append(phone)
                if (type == "taxi") append("\nProposed seats: ").append(value("correction_seats"))
                else append("\nProposed fuel: ").append(value("correction_fuel"))
                append("\nProposed other details: ").append(value("correction_info"))
                append("\nRequested correction: ").append(detail)
            }
            val body = JSONObject().put("category", if (type == "taxi") "Taxi correction" else "Auto correction")
                .put("related_route", driverId.ifBlank { driverName })
                .put("message", details)
                .put("contact", phone)
            sendRequests(listOf(body), "feedback_submissions", "Correction request sent for admin review.")
        }
    }

    private fun feedbackForm() {
        spinner("Feedback category", listOf("Suggestion", "Incorrect information", "App problem", "Other"), "feedback_category")
        field("related_route", "Related route (optional)")
        field("feedback_text", "Describe your feedback")
        field("contact", "Contact number (optional)")
        button("Send feedback") {
            val message = value("feedback_text")
            if (message.length < 4) { toast("Please describe your feedback."); return@button }
            val body = JSONObject().put("category", spinners["feedback_category"]?.selectedItem?.toString() ?: "Other")
                .put("related_route", value("related_route").takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                .put("message", message).put("contact", digits(value("contact")).takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            sendRequests(listOf(body), "feedback_submissions", "Thank you. Feedback sent.")
        }
    }

    private fun sendRequests(bodies: List<JSONObject>, table: String, success: String) {
        if (!CommunitySupabase.isConfigured()) { toast("Online submission is not configured."); return }
        root.isEnabled = false
        lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) { bodies.map { CommunitySupabase.insert(table, it) } }
            root.isEnabled = true
            if (results.all { it }) { toast(success); finish() }
            else toast("Could not send all requests. Check internet and try again.\n" + CommunitySupabase.lastError)
        }
    }


    private fun addMissingPlaceField(target: LinearLayout) {
        val field = AutoCompleteTextView(this).apply {
            hint = "Missing stop name (Punjabi / हिंदी / English)"
            threshold = 1
            textSize = 16f
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        fun languageFor(query: String): String {
            val pa = query.count { it.code in 0x0A00..0x0A7F }
            val hi = query.count { it.code in 0x0900..0x097F }
            val en = query.count { it in 'a'..'z' || it in 'A'..'Z' }
            return when {
                pa > hi && pa > en -> "pa"
                hi > pa && hi > en -> "hi"
                en > 0 && en >= pa && en >= hi -> "en"
                else -> LanguageHelper.current()
            }
        }
        fun labelFor(place: Place, lang: String): String {
            val order = when (lang) { "hi" -> listOf("hi", "en", "pa"); "pa" -> listOf("pa", "hi", "en"); else -> listOf("en", "hi", "pa") }
            return order.mapNotNull { place.names[it]?.trim()?.takeIf { name -> name.isNotBlank() } }
                .distinct().joinToString(" • ").ifBlank { place.displayName(lang) }
        }
        var activeLanguage = LanguageHelper.current()
        var shownPlaces = allPlaces
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, mutableListOf())
        fun refreshSuggestions(query: String) {
            activeLanguage = languageFor(query)
            shownPlaces = allPlaces.filter { place ->
                query.isBlank() || place.names.values.any { it.contains(query, ignoreCase = true) } ||
                    place.displayName(activeLanguage).contains(query, ignoreCase = true)
            }
            adapter.clear()
            adapter.addAll(shownPlaces.map { labelFor(it, activeLanguage) })
            adapter.notifyDataSetChanged()
        }
        refreshSuggestions("")
        field.setAdapter(adapter)
        field.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refreshSuggestions(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        field.setOnItemClickListener { _, _, position, _ ->
            val place = shownPlaces.getOrNull(position)
            if (place != null) {
                field.setText(place.displayName(activeLanguage), false)
                field.setSelection(field.text.length)
            }
        }
        target.addView(field)
        missingPlaceFields.add(field)
    }

    private fun buttonIn(target: LinearLayout, label: String, action: () -> Unit) { target.addView(Button(this).apply { text = label; setOnClickListener { action() } }) }
    private fun headingIn(target: LinearLayout, s: String) { textIn(target, s, 16f, true) }
    private fun textIn(target: LinearLayout, s: String, size: Float, bold: Boolean = false) {
        target.addView(TextView(this).apply { text = s; textSize = size; if (bold) setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, dp(10), 0, dp(5)) })
    }
    private fun fieldIn(target: LinearLayout, key: String, hint: String): EditText {
        val e = EditText(this).apply { this.hint = hint; textSize = 16f; setPadding(dp(12), dp(10), dp(12), dp(10)) }
        fields[key] = e; target.addView(e); return e
    }
    private fun spinnerIn(target: LinearLayout, hint: String, options: List<String>): Spinner {
        val s = Spinner(this); s.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, if (options.isEmpty()) listOf("Loading stops…") else options)
        s.contentDescription = hint; target.addView(s); return s
    }
    private fun timePickerFieldIn(target: LinearLayout, key: String, hint: String): EditText {
        val e = fieldIn(target, key, hint)
        e.isFocusable = false; e.isClickable = true
        e.setOnClickListener {
            val now = java.util.Calendar.getInstance()
            TimePickerDialog(this, { _, hour, minute ->
                e.setText(String.format(Locale.ROOT, "%02d:%02d", hour, minute))
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(e.windowToken, 0)
            }, now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE), true).show()
        }
        return e
    }

    private fun routeLabel(route: Route): String = "${placeNames[route.fromId] ?: route.fromId} → ${placeNames[route.toId] ?: route.toId} (${route.time})"
    private fun heading(s: String) { text(s, 16f, true) }
    private fun text(s: String, size: Float, bold: Boolean = false) { root.addView(TextView(this).apply { text = s; textSize = size; if (bold) setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, dp(10), 0, dp(5)) }) }
    private fun field(key: String, hint: String): EditText { val e = EditText(this).apply { this.hint = hint; textSize = 16f; setPadding(dp(12), dp(10), dp(12), dp(10)) }; fields[key] = e; root.addView(e); return e }
    private fun spinner(hint: String, options: List<String>, key: String? = null): Spinner {
        val s = Spinner(this); s.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, if (options.isEmpty()) listOf("Loading stops…") else options)
        s.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
                v.clearFocus()
            }
            false
        }
        root.addView(s); if (key != null) spinners[key] = s; return s
    }
    private fun button(label: String, action: () -> Unit) { root.addView(Button(this).apply { text = label; setOnClickListener { action() } }) }
    private fun value(key: String) = fields[key]?.text?.toString()?.trim().orEmpty()
    private fun digits(s: String) = s.filter(Char::isDigit)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
}
