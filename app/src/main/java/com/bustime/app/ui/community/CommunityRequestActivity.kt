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
        title = when (mode) { "correction" -> "Route Correction"; "vehicle" -> "List Auto / Taxi"; else -> "Feedback" }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(24)) }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        text("${title}\nYour submission will be reviewed by the admin before it affects the public app.", 18f)
        when (mode) {
            "correction" -> loadCorrectionForm()
            "vehicle" -> vehicleForm()
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
            routeSpinner?.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    lifecycleScope.launch { loadStopsFor(routes[position]) }
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
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
        spinner("Vehicle type", listOf("Auto", "Taxi"), "vehicle_type")
        field("person_name", "Driver / contact person name")
        field("vehicle_name", "Vehicle name / model")
        field("seats", "Number of seats (if applicable)")
        field("location", "Location / service area")
        field("phone", "Mobile number")
        field("details", "Other information (optional)")
        button("Submit listing for admin review") {
            val type = if (spinners["vehicle_type"]?.selectedItemPosition == 0) "auto" else "taxi"
            val name = value("person_name"); val vehicle = value("vehicle_name"); val phone = digits(value("phone"))
            val seats = value("seats").toIntOrNull()
            if (name.isBlank() || vehicle.isBlank() || value("location").isBlank() || phone.length !in 10..13 || (type == "taxi" && (seats == null || seats < 1))) { toast("Enter name, vehicle, location and valid mobile number. Taxi listings also need seat count."); return@button }
            val body = JSONObject().put("vehicle_type", type).put("person_name", name).put("vehicle_name", vehicle)
                .put("seats", seats ?: JSONObject.NULL).put("location", value("location"))
                .put("phone", phone).put("details", value("details").takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            sendRequests(listOf(body), "vehicle_submissions", "Listing sent for admin review.")
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
    private fun field(key: String, hint: String) { val e = EditText(this).apply { this.hint = hint; textSize = 16f; setPadding(dp(12), dp(10), dp(12), dp(10)) }; fields[key] = e; root.addView(e) }
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
