package com.bustime.app.ui.addroute

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bustime.app.R
import com.bustime.app.data.repository.AppRepository
import com.bustime.app.models.Route
import com.bustime.app.utils.LanguageHelper
import com.bustime.app.utils.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Existing route timing updates and free-text submissions for entirely new routes. */
class AddRouteActivity : AppCompatActivity() {
    private class ExistingStop(val placeId: String, val timeView: TextView, var time: String? = null)
    private class NewStop(val name: EditText, val timeView: TextView, var time: String? = null)

    private lateinit var repo: AppRepository
    private lateinit var tvStart: TextView
    private lateinit var stopsBox: LinearLayout
    private lateinit var newStopsBox: LinearLayout
    private lateinit var btnSubmit: Button
    private lateinit var cbNewRoute: CheckBox
    private lateinit var newRouteFields: LinearLayout
    private var routes: List<Route> = emptyList()
    private var selected: Route? = null
    private var startTime: String? = null
    private val existingRows = ArrayList<ExistingStop>()
    private val newRows = ArrayList<NewStop>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_route)
        repo = AppRepository.getInstance(this)
        findViewById<Button>(R.id.btnBackAdd).setOnClickListener { finish() }
        tvStart = findViewById(R.id.tvStartTime)
        stopsBox = findViewById(R.id.stopsBox)
        newStopsBox = findViewById(R.id.newStopsBox)
        btnSubmit = findViewById(R.id.btnSubmitRoute)
        cbNewRoute = findViewById(R.id.cbNewRoute)
        newRouteFields = findViewById(R.id.newRouteFields)
        val spinner = findViewById<Spinner>(R.id.spRoute)
        cbNewRoute.setOnCheckedChangeListener { _, isNew ->
            newRouteFields.visibility = if (isNew) View.VISIBLE else View.GONE
            spinner.visibility = if (isNew) View.GONE else View.VISIBLE
            stopsBox.visibility = if (isNew) View.GONE else View.VISIBLE
        }
        findViewById<Button>(R.id.btnAddNewStop).setOnClickListener { addNewStop() }
        tvStart.setOnClickListener { pickTime(startTime) { startTime = it; showTime(tvStart, it) } }
        btnSubmit.setOnClickListener { submit() }

        lifecycleScope.launch {
            routes = repo.getAllRoutes().distinctBy { it.fromId + ">" + it.toId }
            val ids = routes.flatMap { listOf(it.fromId, it.toId) }.toSet()
            val names = repo.getPlacesById(ids)
            val lang = LanguageHelper.current()
            val labels = routes.map { "${names[it.fromId]?.displayName(lang) ?: it.fromId} → ${names[it.toId]?.displayName(lang) ?: it.toId}" }
            spinner.adapter = ArrayAdapter(this@AddRouteActivity, android.R.layout.simple_spinner_dropdown_item, labels)
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position in routes.indices) loadStops(routes[position])
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
            if (routes.isEmpty()) {
                cbNewRoute.isChecked = true
                spinner.visibility = View.GONE
            }
        }
    }

    private fun loadStops(route: Route) {
        selected = route
        lifecycleScope.launch {
            val stops = repo.getRouteStops(route.id)
            val names = repo.getPlacesById(stops.map { it.placeId }.toSet())
            val lang = LanguageHelper.current()
            stopsBox.removeAllViews(); existingRows.clear()
            val pad = (10 * resources.displayMetrics.density).toInt()
            for (stop in stops.drop(1)) {
                val line = LinearLayout(this@AddRouteActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, pad, 0, pad) }
                val name = TextView(this@AddRouteActivity).apply { text = names[stop.placeId]?.displayName(lang) ?: stop.placeId; textSize = 16f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
                val time = TextView(this@AddRouteActivity).apply { text = getString(R.string.ar_tap_time); textSize = 15f; setBackgroundColor(0xFFFFFFFF.toInt()); setPadding(pad, pad, pad, pad) }
                val row = ExistingStop(stop.placeId, time)
                time.setOnClickListener { pickTime(row.time) { t -> row.time = t; showTime(time, t) } }
                line.addView(name); line.addView(time); stopsBox.addView(line); existingRows.add(row)
            }
        }
    }

    private fun addNewStop() {
        val pad = (8 * resources.displayMetrics.density).toInt()
        val line = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, pad, 0, pad) }
        val name = EditText(this).apply { hint = "पड़ाव का नाम / Stop name"; setSingleLine(true); textSize = 16f }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val time = TextView(this).apply { text = getString(R.string.ar_tap_time); setPadding(pad, pad, pad, pad); setBackgroundColor(0xFFFFFFFF.toInt()) }
        val remove = Button(this).apply { text = "−"; setOnClickListener { val row = newRows.find { it.name === name }; if (row != null) newRows.remove(row); newStopsBox.removeView(line) } }
        val row = NewStop(name, time)
        time.setOnClickListener { pickTime(row.time) { t -> row.time = t; showTime(time, t) } }
        controls.addView(time, LinearLayout.LayoutParams(0, -2, 1f)); controls.addView(remove)
        line.addView(name); line.addView(controls); newStopsBox.addView(line); newRows.add(row)
    }

    private fun showTime(view: TextView, time: String?) { view.text = if (time == null) getString(R.string.ar_tap_time) else TimeFormat.format(time) }
    private fun pickTime(current: String?, onPicked: (String?) -> Unit) {
        val h = current?.substringBefore(":")?.toIntOrNull() ?: 8
        val m = current?.substringAfter(":", "")?.toIntOrNull() ?: 0
        val dialog = TimePickerDialog(this, { _, hh, mm -> onPicked("%02d:%02d".format(hh, mm)) }, h, m, false)
        dialog.setButton(android.content.DialogInterface.BUTTON_NEUTRAL, getString(R.string.ar_clear)) { _, _ -> onPicked(null) }
        dialog.show()
    }

    private fun submit() {
        if (!SupabaseClient.isConfigured()) { Toast.makeText(this, R.string.ar_not_set, Toast.LENGTH_LONG).show(); return }
        val isNew = cbNewRoute.isChecked
        val fromText = findViewById<EditText>(R.id.etFromText).text.toString().trim()
        val toText = findViewById<EditText>(R.id.etToText).text.toString().trim()
        val route = selected
        if (isNew && (fromText.isBlank() || toText.isBlank())) { Toast.makeText(this, "From और To दोनों जगहों के नाम लिखें", Toast.LENGTH_SHORT).show(); return }
        if (!isNew && route == null) { Toast.makeText(this, "एक रूट चुनें या नया रूट जोड़ें", Toast.LENGTH_SHORT).show(); return }
        val phone = findViewById<EditText>(R.id.etPhone).text.toString().filter { it.isDigit() }
        if (phone.length !in 10..13) { Toast.makeText(this, R.string.ar_need_phone, Toast.LENGTH_SHORT).show(); return }
        val service = findViewById<EditText>(R.id.etService).text.toString().trim()
        val stops = JSONArray()
        var anyTime = startTime != null
        if (isNew) {
            for (row in newRows) {
                val name = row.name.text.toString().trim()
                if (name.isNotBlank()) {
                    if (row.time != null) anyTime = true
                    stops.put(JSONObject().put("name", name).put("time", row.time ?: JSONObject.NULL))
                }
            }
        } else {
            for (row in existingRows) {
                if (row.time != null) anyTime = true
                stops.put(JSONObject().put("place", row.placeId).put("time", row.time ?: JSONObject.NULL))
            }
        }
        if (!anyTime) { Toast.makeText(this, "कम-से-कम एक समय दें", Toast.LENGTH_LONG).show(); return }
        val body = JSONObject()
            .put("submission_type", if (isNew) "new_route" else "existing_route")
            .put("from_id", if (!isNew) route!!.fromId else JSONObject.NULL)
            .put("to_id", if (!isNew) route!!.toId else JSONObject.NULL)
            .put("from_text", if (isNew) fromText else JSONObject.NULL)
            .put("to_text", if (isNew) toText else JSONObject.NULL)
            .put("route_text", if (isNew) "$fromText → $toText" else JSONObject.NULL)
            .put("start_time", startTime ?: JSONObject.NULL)
            .put("service_name", if (service.isBlank()) JSONObject.NULL else service)
            .put("stops", stops)
            .put("phone", phone)
        btnSubmit.isEnabled = false
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { SupabaseClient.insertRouteSubmission(body) }
            if (ok) { Toast.makeText(this@AddRouteActivity, R.string.ar_sent, Toast.LENGTH_LONG).show(); finish() }
            else { btnSubmit.isEnabled = true; Toast.makeText(this@AddRouteActivity, R.string.ar_failed, Toast.LENGTH_LONG).show() }
        }
    }
}
