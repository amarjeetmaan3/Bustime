package com.bustime.app.ui.community

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: "feedback"
        title = when (mode) { "correction" -> "Route Correction"; "vehicle" -> "Add Auto / Taxi"; "vehicle_correction" -> if (intent.getStringExtra("vehicle_type") == "taxi") "Taxi Correction" else "Auto Correction"; else -> "Feedback" }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(24)) }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        text("${title}\nYour submission will be reviewed by the admin before it affects the public app.", 18f)
        when (mode) {
            "correction" -> loadCorrectionForm()
            "vehicle" -> vehicleForm()
            "bus_operator" -> busOperatorForm()
            "vehicle_correction" -> vehicleCorrectionForm()
            else -> feedbackForm()
        }
    }

    private fun loadCorrectionForm() {
        if (!CommunitySupabase.isConfigured()) { text("Online submissions are not configured yet.", 14f); return }
        lifecycleScope.launch {
            routes = AppRepository.getInstance(this@CommunityRequestActivity).getAllRoutes()
            if (routes.isEmpty()) { text("No bus routes are available yet.", 14f); return@launch }
            val repo = AppRepository.getInstance(this@CommunityRequestActivity)
            allPlaces = repo.getAllPlaces()
            placeNames = allPlaces.associate { it.id to it.displayName(LanguageHelper.current()) }
            routeSpinner = spinner("Choose bus route", routes.map { routeLabel(it) })
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
            heading("What needs correction?")
            missingCheck = CheckBox(this@CommunityRequestActivity).apply { text = "Missing Stop"; isChecked = true }
            timeCheck = CheckBox(this@CommunityRequestActivity).apply { text = "Correct Time" }
            root.addView(missingCheck); root.addView(timeCheck)
            heading("Missing Stop details")
            text("Choose the existing stops between which the missing stop belongs. You can select a known place or type a new place name.", 14f)
            stopOne = spinner("Stop before missing stop", emptyList())
            stopTwo = spinner("Stop after missing stop", emptyList())
            missingExisting = spinner("Choose existing place (optional)", listOf("— Type a new place below —"))
            field("missing_place", "Or type missing place name")
            field("missing_time", "Missing stop time (HH:MM, optional)")
            heading("Correct Time details")
            correctedStop = spinner("Choose stop to correct", emptyList())
            field("corrected_time", "Correct time (HH:MM)")
            field("correction_note", "Extra details (optional)")
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
        update(stopOne, labels.dropLast(1).ifEmpty { labels })
        update(stopTwo, labels.drop(1).ifEmpty { labels })
        update(correctedStop, labels)
        update(missingExisting, listOf("— Type a new place below —") + allPlaces.map { it.displayName(LanguageHelper.current()) })
    }

    private fun submitCorrection() {
        val route = routes.getOrNull(routeSpinner?.selectedItemPosition ?: -1) ?: return
        val sendMissing = missingCheck?.isChecked == true
        val sendTime = timeCheck?.isChecked == true
        if (!sendMissing && !sendTime) { toast("Select Missing Stop, Correct Time, or both."); return }
        val note = value("correction_note")
        val jobs = mutableListOf<Pair<String, JSONObject>>()
        if (sendMissing) {
            val before = routeStops.getOrNull(stopOne?.selectedItemPosition ?: -1)
            val afterIndex = (stopTwo?.selectedItemPosition ?: -1) + 1
            val after = routeStops.getOrNull(afterIndex)
            val existingIndex = (missingExisting?.selectedItemPosition ?: 0) - 1
            val typed = value("missing_place")
            val existingPlace = allPlaces.getOrNull(existingIndex)
            val existingName = existingPlace?.displayName(LanguageHelper.current()).orEmpty()
            val missing = typed.ifBlank { existingName }
            if (before == null || after == null || after.stopOrder != before.stopOrder + 1 || missing.isBlank()) {
                toast("Choose the stops around the missing stop and select/type its name."); return
            }
            val t = value("missing_time")
            if (t.isNotBlank() && !t.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$"))) { toast("Enter missing-stop time as HH:MM."); return }
            jobs += "missing_stop" to JSONObject().put("route_id", route.id).put("request_type", "missing_stop")
                .put("before_stop_id", before.placeId).put("after_stop_id", after.placeId)
                .put("missing_place_name", missing).put("missing_place_id", if (typed.isBlank()) existingPlace?.id ?: JSONObject.NULL else JSONObject.NULL)
                .put("missing_time", t.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                .put("notes", note.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
        }
        if (sendTime) {
            val stop = routeStops.getOrNull(correctedStop?.selectedItemPosition ?: -1)
            val t = value("corrected_time")
            if (stop == null || !t.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$"))) { toast("Choose a stop and enter the corrected time as HH:MM."); return }
            jobs += "correct_time" to JSONObject().put("route_id", route.id).put("request_type", "correct_time")
                .put("stop_id", stop.placeId).put("corrected_time", t).put("notes", note.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
        }
        sendRequests(jobs.map { it.second }, "correction_submissions", "Correction request(s) sent for admin review.")
    }

    private fun vehicleForm() {
        val requestedType = intent.getStringExtra("vehicle_type")
        val typeSpinner = spinner("Vehicle type", listOf("Auto", "Taxi"), "vehicle_type")
        if (requestedType == "taxi") typeSpinner.setSelection(1)
        if (requestedType == "auto" || requestedType == "taxi") typeSpinner.visibility = View.GONE
        field("person_name", "Driver / contact person name")
        field("vehicle_name", "Vehicle name / model")
        field("seats", "Number of seats (if applicable)")
        field("location", "Location / address")
        field("area_name", "Service area (e.g. Sadhusar / Sri Ganganagar)")
        field("union_name", "Union name (optional)")
        field("phone", "Mobile number")
        field("details", "Other information (optional)")
        button("Submit listing for admin review") {
            val type = if (spinners["vehicle_type"]?.selectedItemPosition == 0) "auto" else "taxi"
            val name = value("person_name"); val vehicle = value("vehicle_name"); val phone = digits(value("phone"))
            val seats = value("seats").toIntOrNull()
            if (name.isBlank() || vehicle.isBlank() || value("location").isBlank() || phone.length !in 10..13 || (type == "taxi" && (seats == null || seats < 1))) { toast("Enter name, vehicle, location and valid mobile number. Taxi listings also need seat count."); return@button }
            val area = value("area_name")
            if (area.isBlank()) { toast("Enter the driver's service area. This is separate from bus stands."); return@button }
            val body = JSONObject().put("vehicle_type", type).put("person_name", name).put("vehicle_name", vehicle)
                .put("seats", seats ?: JSONObject.NULL).put("location", value("location"))
                .put("area_name", area)
                .put("union_name", value("union_name").takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                .put("phone", phone).put("details", value("details").takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            sendRequests(listOf(body), "vehicle_submissions", "Listing sent for admin review.")
        }
    }

    private fun busOperatorForm() {
        heading("Add Your Bus")
        text("Submit bus details for admin verification. The listing becomes public only after approval.", 14f)
        field("operator_name", "Bus owner / operator name")
        field("service_name", "Service name")
        spinner("Bus category", listOf("Ordinary", "Luxury", "Sleeper", "Seater", "Sleeper + Seater", "Custom"), "bus_type")
        field("custom_category", "Custom category name (if Custom, e.g. Prime A)")
        spinner("Seat configuration", listOf("Not specified", "Sleeper", "Seater", "Sleeper + Seater"), "seat_type")
        field("sleeper_count", "Number of sleeper berths")
        field("seater_count", "Number of seats")
        field("from_text", "Starting city / area")
        field("to_text", "Destination city / area")
        field("location", "Operating area (optional)")
        field("phone", "Contact mobile number")
        field("whatsapp", "WhatsApp number (optional)")
        field("details", "Additional details (optional)")
        button("Submit bus for admin review") {
            val operator = value("operator_name")
            val phone = digits(value("phone"))
            val category = spinners["bus_type"]?.selectedItem?.toString() ?: "Ordinary"
            val custom = value("custom_category")
            val seatsType = spinners["seat_type"]?.selectedItem?.toString()?.takeUnless { it == "Not specified" }
            val sleeper = value("sleeper_count").toIntOrNull()
            val seater = value("seater_count").toIntOrNull()
            if (operator.isBlank() || phone.length !in 10..13) { toast("Enter operator name and a valid mobile number."); return@button }
            if (category == "Custom" && custom.isBlank()) { toast("Enter your custom bus category."); return@button }
            if ((sleeper != null && sleeper < 0) || (seater != null && seater < 0)) { toast("Seat counts cannot be negative."); return@button }
            val body = JSONObject()
                .put("operator_name", operator)
                .put("service_name", value("service_name").takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                .put("bus_type", if (category == "Custom") custom else category)
                .put("custom_category", custom.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                .put("seat_type", seatsType ?: JSONObject.NULL)
                .put("sleeper_count", sleeper ?: JSONObject.NULL)
                .put("seater_count", seater ?: JSONObject.NULL)
                .put("from_text", value("from_text").takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                .put("to_text", value("to_text").takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                .put("location", value("location").takeIf { it.isNotBlank() } ?: JSONObject.NULL)
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
            else toast("Could not send all requests. Check internet and try again; no request was merged with another.")
        }
    }

    private fun routeLabel(route: Route): String = "${placeNames[route.fromId] ?: route.fromId} → ${placeNames[route.toId] ?: route.toId} (${route.time})"
    private fun heading(s: String) { text(s, 16f, true) }
    private fun text(s: String, size: Float, bold: Boolean = false) { root.addView(TextView(this).apply { text = s; textSize = size; if (bold) setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, dp(10), 0, dp(5)) }) }
    private fun field(key: String, hint: String): EditText { val e = EditText(this).apply { this.hint = hint; textSize = 16f; setPadding(dp(12), dp(10), dp(12), dp(10)) }; fields[key] = e; root.addView(e); return e }
    private fun spinner(hint: String, options: List<String>, key: String? = null): Spinner {
        val s = Spinner(this); s.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, if (options.isEmpty()) listOf("Loading stops…") else options)
        root.addView(s); if (key != null) spinners[key] = s; return s
    }
    private fun button(label: String, action: () -> Unit) { root.addView(Button(this).apply { text = label; setOnClickListener { action() } }) }
    private fun value(key: String) = fields[key]?.text?.toString()?.trim().orEmpty()
    private fun digits(s: String) = s.filter(Char::isDigit)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
}
