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

/** Community request forms; route corrections submit a complete proposed ordered stop list. */
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


    private var correctionEditor: LinearLayout? = null
    private var correctionRoute: Route? = null
    private val correctionDrafts = mutableListOf<JSONObject>()

    private fun loadCorrectionForm() {
        if (!CommunitySupabase.isConfigured()) { text(getString(com.bustime.app.R.string.community_online_not_configured), 14f); return }
        lifecycleScope.launch {
            val repo = AppRepository.getInstance(this@CommunityRequestActivity)
            routes = repo.getAllRoutes()
            if (routes.isEmpty()) { text(getString(com.bustime.app.R.string.community_no_routes), 14f); return@launch }
            allPlaces = repo.getAllPlaces()
            routeSpinner = spinner(getString(com.bustime.app.R.string.community_choose_route), routes.map { routeLabel(it) })
            routeSpinner?.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    lifecycleScope.launch { loadStopsFor(routes[position]) }
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
            val requested = intent.getStringExtra("route_id")
            if (requested != null) {
                val i = routes.indexOfFirst { it.id == requested }
                if (i >= 0) routeSpinner?.setSelection(i)
            }
            heading("Complete route correction")
            text("Replace an incorrect stop by selecting the correct place. Add a new start at the top or add stops between/at the end. Time is optional. Changes go to Admin for approval first.", 14f)
            correctionEditor = LinearLayout(this@CommunityRequestActivity).apply { orientation = LinearLayout.VERTICAL }
            root.addView(correctionEditor!!)
            field("correction_note", "Extra note for admin (optional)")
            field("phone", "Mobile number (required)")
            button("Submit complete route correction") { submitCorrection() }
            loadStopsFor(routes.getOrNull(routeSpinner?.selectedItemPosition ?: 0) ?: routes.first())
        }
    }

    private suspend fun loadStopsFor(route: Route) {
        correctionRoute = route
        routeStops = AppRepository.getInstance(this).getRouteStops(route.id).sortedBy { it.stopOrder }
        val places = AppRepository.getInstance(this).getPlacesById(routeStops.map { it.placeId }.toSet())
        placeNames = places.mapValues { it.value.displayName(LanguageHelper.current()) }
        correctionDrafts.clear()
        routeStops.forEach { stop ->
            correctionDrafts.add(JSONObject().put("place_id", stop.placeId).put("name", placeNames[stop.placeId] ?: stop.placeId)
                .put("language", LanguageHelper.current()).put("time", stop.time ?: JSONObject.NULL))
        }
        renderCorrectionList()
    }

    private fun renderCorrectionList() {
        val box = correctionEditor ?: return
        box.removeAllViews()
        val bg = android.util.TypedValue()
        val fg = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.colorBackground, bg, true)
        theme.resolveAttribute(android.R.attr.textColorPrimary, fg, true)
        val surfaceColor = bg.data
        val textColor = fg.data

        // Compact, theme-aware rows: no large white cards and no four-button toolbar per stop.
        val addStart = Button(this).apply {
            text = "＋ Add Start"
            setTextColor(android.graphics.Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.rgb(32, 145, 75))
            setOnClickListener {
                correctionDrafts.add(0, JSONObject().put("place_id", JSONObject.NULL).put("name", "").put("language", LanguageHelper.current()).put("time", JSONObject.NULL))
                renderCorrectionList()
            }
        }
        box.addView(addStart, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(8) })
        correctionDrafts.forEachIndexed { index, draft ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(3), dp(2), dp(3), dp(2))
                minimumHeight = dp(54)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(surfaceColor)
                    cornerRadius = dp(7).toFloat()
                }
            }
            val number = TextView(this).apply {
                text = (index + 1).toString()
                textSize = 13f
                setTextColor(textColor)
                gravity = android.view.Gravity.CENTER
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (index == 0 || index == correctionDrafts.lastIndex) android.graphics.Color.rgb(44, 115, 83) else android.graphics.Color.rgb(83, 91, 108))
                    cornerRadius = dp(14).toFloat()
                }
                minWidth = dp(28)
                setPadding(dp(5), dp(3), dp(5), dp(3))
            }
            row.addView(number, LinearLayout.LayoutParams(dp(30), dp(28)).apply { marginEnd = dp(5) })

            val name = AutoCompleteTextView(this).apply {
                hint = if (index == 0) "Start stop" else if (index == correctionDrafts.lastIndex) "Destination" else "Stop name"
                threshold = 1
                textSize = 14f
                setSingleLine(true)
                setTextColor(textColor)
                setHintTextColor(if (textColor == android.graphics.Color.WHITE) 0xFFAAAAAA.toInt() else 0xFF777777.toInt())
                setPadding(dp(5), 0, dp(3), 0)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setText(draft.optString("name"), false)
            }
            val initialLang = draft.optString("language", LanguageHelper.current())
            fun suggestionLabel(p: Place, lang: String): String {
                val order = when (lang) { "pa" -> listOf("pa", "hi", "en"); "hi" -> listOf("hi", "en", "pa"); else -> listOf("en", "hi", "pa") }
                return order.mapNotNull { p.names[it]?.trim()?.takeIf { v -> v.isNotBlank() } }.distinct().joinToString(" • ")
            }
            var filteredPlaces = allPlaces.toList()
            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, filteredPlaces.map { suggestionLabel(it, initialLang) })
            name.setAdapter(adapter)
            name.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val q = s?.toString().orEmpty()
                    draft.put("name", q).put("place_id", JSONObject.NULL)
                    val lang = when {
                        q.any { it.code in 0x0A00..0x0A7F } -> "pa"
                        q.any { it.code in 0x0900..0x097F } -> "hi"
                        q.any { it in 'a'..'z' || it in 'A'..'Z' } -> "en"
                        else -> LanguageHelper.current()
                    }
                    draft.put("language", lang)
                    filteredPlaces = allPlaces.filter { p ->
                        (q.isBlank() || p.names.values.any { it.contains(q, true) }) &&
                            correctionDrafts.none { other -> other !== draft && !other.isNull("place_id") && other.optString("place_id") == p.id }
                    }
                    adapter.clear()
                    adapter.addAll(filteredPlaces.map { suggestionLabel(it, lang) })
                    adapter.notifyDataSetChanged()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
            name.setOnItemClickListener { _, _, pos, _ ->
                val p = filteredPlaces.getOrNull(pos)
                if (p != null) {
                    val lang = draft.optString("language", LanguageHelper.current())
                    draft.put("place_id", p.id).put("name", p.displayName(lang))
                    name.setText(p.displayName(lang), false)
                }
            }
            row.addView(name, LinearLayout.LayoutParams(0, dp(44), 1f))

            val time = TextView(this).apply {
                val t = if (draft.isNull("time")) "" else draft.optString("time")
                text = if (t.isBlank()) "＋ time" else t
                textSize = 12f
                gravity = android.view.Gravity.CENTER
                setTextColor(if (t.isBlank()) textColor else android.graphics.Color.rgb(58, 133, 190))
                setPadding(dp(5), dp(4), dp(5), dp(4))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (t.isBlank()) android.graphics.Color.TRANSPARENT else if ((resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES) 0xFF263746.toInt() else 0xFFEAF3FA.toInt())
                    cornerRadius = dp(6).toFloat()
                }
                setOnClickListener {
                    val cal = java.util.Calendar.getInstance()
                    val parts = draft.optString("time").split(":")
                    val h = parts.getOrNull(0)?.toIntOrNull() ?: cal.get(java.util.Calendar.HOUR_OF_DAY)
                    val m = parts.getOrNull(1)?.toIntOrNull() ?: cal.get(java.util.Calendar.MINUTE)
                    TimePickerDialog(this@CommunityRequestActivity, { _, hh, mm ->
                        val value = String.format(Locale.ROOT, "%02d:%02d", hh, mm)
                        draft.put("time", value)
                        text = value
                        setTextColor(android.graphics.Color.rgb(58, 133, 190))
                    }, h, m, true).show()
                }
            }
            row.addView(time, LinearLayout.LayoutParams(dp(56), dp(36)).apply { marginStart = dp(3) })

            val add = TextView(this).apply {
                text = "+"
                textSize = 23f
                gravity = android.view.Gravity.CENTER
                setTextColor(android.graphics.Color.WHITE)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.rgb(32, 145, 75))
                    cornerRadius = dp(7).toFloat()
                }
                contentDescription = "Add stop below"
                setOnClickListener {
                    draft.put("name", name.text.toString().trim())
                    val newStop = JSONObject().put("place_id", JSONObject.NULL).put("name", "").put("language", LanguageHelper.current()).put("time", JSONObject.NULL)
                    correctionDrafts.add(index + 1, newStop)
                    renderCorrectionList()
                }
            }
            row.addView(add, LinearLayout.LayoutParams(dp(34), dp(36)).apply { marginStart = dp(4) })

            val more = TextView(this).apply {
                text = "⋮"
                textSize = 23f
                gravity = android.view.Gravity.CENTER
                setTextColor(textColor)
                contentDescription = "More stop actions"
                setOnClickListener { anchor ->
                    draft.put("name", name.text.toString().trim())
                    val menu = android.widget.PopupMenu(this@CommunityRequestActivity, anchor)
                    menu.menu.add("Move up").isEnabled = index > 0
                    menu.menu.add("Move down").isEnabled = index < correctionDrafts.lastIndex
                    menu.menu.add("Remove stop").setIcon(android.R.drawable.ic_menu_delete)
                    menu.setOnMenuItemClickListener { item ->
                        when (item.title.toString()) {
                            "Move up" -> if (index > 0) { val v = correctionDrafts.removeAt(index); correctionDrafts.add(index - 1, v); renderCorrectionList() }
                            "Move down" -> if (index < correctionDrafts.lastIndex) { val v = correctionDrafts.removeAt(index); correctionDrafts.add(index + 1, v); renderCorrectionList() }
                            "Remove stop" -> android.app.AlertDialog.Builder(this@CommunityRequestActivity)
                                .setTitle("Remove stop?")
                                .setMessage("This removes it only from your proposed correction. The live route stays unchanged until Admin approval.")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Remove") { _, _ -> correctionDrafts.removeAt(index); renderCorrectionList() }.show()
                        }
                        true
                    }
                    menu.show()
                }
            }
            row.addView(more, LinearLayout.LayoutParams(dp(28), dp(40)).apply { marginStart = dp(1) })
            box.addView(row, LinearLayout.LayoutParams(-1, dp(58)).apply { setMargins(0, dp(1), 0, dp(1)) })
            val divider = View(this).apply { setBackgroundColor(if (textColor == android.graphics.Color.WHITE) 0xFF393939.toInt() else 0xFFE0E0E0.toInt()) }
            box.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))
        }
        val addAtEnd = Button(this).apply {
            text = "＋ Add stop at end"
            setTextColor(android.graphics.Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.rgb(32, 145, 75))
            setOnClickListener {
                correctionDrafts.add(JSONObject().put("place_id", JSONObject.NULL).put("name", "").put("language", LanguageHelper.current()).put("time", JSONObject.NULL))
                renderCorrectionList()
            }
        }
        box.addView(addAtEnd, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })
    }

    private fun submitCorrection() {
        val route = correctionRoute ?: routes.getOrNull(routeSpinner?.selectedItemPosition ?: -1) ?: return
        val phone = digits(value("phone"))
        if (phone.length !in 10..13) { toast("Enter a valid mobile number before submitting."); return }
        if (correctionDrafts.size < 2) { toast("A route needs at least a start and destination stop."); return }
        val proposed = org.json.JSONArray()
        val seen = mutableMapOf<String, Int>()
        correctionDrafts.forEachIndexed { index, d ->
            val name = d.optString("name").trim()
            if (name.isBlank()) { toast("Enter a name for stop " + (index + 1) + "."); return }
            val t = d.optString("time").trim()
            if (t.isNotBlank() && !t.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$"))) { toast("Stop " + (index + 1) + " has an invalid time."); return }
            val id = if (d.isNull("place_id")) "" else d.optString("place_id")
            val key = (id.ifBlank { name }).lowercase(Locale.ROOT)
            seen[key] = (seen[key] ?: 0) + 1
            proposed.put(JSONObject().put("place_id", id.ifBlank { JSONObject.NULL }).put("name", name)
                .put("language", d.optString("language", LanguageHelper.current()))
                .put("time", t.ifBlank { JSONObject.NULL }).put("position", index))
        }
        if (seen.values.any { it > 1 }) {
            toast("A stop is repeated in this route. Please choose a different stop or remove the duplicate before submitting.")
            return
        }
        sendRouteEdit(route, proposed, phone)
    }

    private fun sendRouteEdit(route: Route, proposed: org.json.JSONArray, phone: String) {
        val original = org.json.JSONArray()
        routeStops.sortedBy { it.stopOrder }.forEachIndexed { index, stop ->
            original.put(JSONObject().put("place_id", stop.placeId).put("time", stop.time ?: JSONObject.NULL).put("position", index))
        }
        val body = JSONObject().put("route_id", route.id).put("request_type", "route_edit")
            .put("original_stops", original).put("proposed_stops", proposed)
            .put("change_summary", "Complete route list edited by user")
            .put("notes", value("correction_note").takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("phone", phone)
        sendRequests(listOf(body), "correction_submissions", "Complete route correction sent for admin review.")
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
