package com.bustime.app.ui.addroute

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
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
    private class ExistingStop(val placeId: String, val timeView: TextView, var time: String? = null)
    private class NewStop(val name: EditText, val timeView: TextView, var time: String? = null)

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
        findViewById<Button>(R.id.btnBackAdd).setOnClickListener { finish() }
        tvStart = findViewById(R.id.tvStartTime)
        stopsBox = findViewById(R.id.stopsBox)
        newStopsBox = findViewById(R.id.newStopsBox)
        btnSubmit = findViewById(R.id.btnSubmitRoute)
        val spinner = findViewById<Spinner>(R.id.spRoute)
        val toggle = findViewById<MaterialButtonToggleGroup>(R.id.toggleMode)

        toggle.addOnButtonCheckedListener { _, checkedId, checked ->
            if (checked) setMode(checkedId == R.id.btnModeNew)
        }
        setMode(false)

        findViewById<Button>(R.id.btnAddNewStop).setOnClickListener { addNewStop() }
        tvStart.setOnClickListener {
            pickTime(startTime) { t ->
                if (t == null || fitsOrder(replaceStart = t)) { startTime = t; showTime(tvStart, t) }
            }
        }
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
            // कोई रूट बना ही नहीं है तो सीधे New Route खुले
            if (routes.isEmpty()) toggle.check(R.id.btnModeNew)
        }
    }

    private fun setMode(newMode: Boolean) {
        isNew = newMode
        findViewById<View>(R.id.layoutExisting).visibility = if (newMode) View.GONE else View.VISIBLE
        findViewById<View>(R.id.layoutExistingStops).visibility = if (newMode) View.GONE else View.VISIBLE
        findViewById<View>(R.id.layoutNew).visibility = if (newMode) View.VISIBLE else View.GONE
        findViewById<View>(R.id.layoutNewStops).visibility = if (newMode) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.tvModeHint).setText(if (newMode) R.string.ar_new_route_intro else R.string.ar_existing_route_intro)
    }

    private fun loadStops(route: Route) {
        selected = route
        lifecycleScope.launch {
            val stops = repo.getRouteStops(route.id)
            val names = repo.getPlacesById(stops.map { it.placeId }.toSet())
            val lang = LanguageHelper.current()
            stopsBox.removeAllViews(); existingRows.clear()
            val pad = (10 * resources.displayMetrics.density).toInt()
            // पहला पड़ाव = चलने की जगह (उसका समय ऊपर "चलने का समय" है), इसलिए उसे छोड़ते हैं
            for (stop in stops.drop(1)) {
                val line = LinearLayout(this@AddRouteActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, pad / 2, 0, pad / 2) }
                val name = TextView(this@AddRouteActivity).apply {
                    text = names[stop.placeId]?.displayName(lang) ?: stop.placeId
                    textSize = 16f
                    setTextColor(getColor(R.color.textPrimary))
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                }
                val time = timeBox()
                val row = ExistingStop(stop.placeId, time)
                time.setOnClickListener {
                    pickTime(row.time) { t ->
                        if (t == null || fitsOrder(existingRow = row, existingTime = t)) { row.time = t; showTime(time, t) }
                    }
                }
                line.addView(name); line.addView(time); stopsBox.addView(line); existingRows.add(row)
            }
        }
    }

    private fun addNewStop() {
        val pad = (8 * resources.displayMetrics.density).toInt()
        val line = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, pad, 0, pad) }
        val name = EditText(this).apply {
            hint = getString(R.string.ar_custom_stop_name)
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
        if (isNew && (fromText.isBlank() || toText.isBlank())) { Toast.makeText(this, R.string.ar_custom_need_endpoints, Toast.LENGTH_SHORT).show(); return }
        if (!isNew && route == null) { Toast.makeText(this, R.string.ar_pick_route, Toast.LENGTH_SHORT).show(); return }
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
        if (!anyTime) { Toast.makeText(this, R.string.ar_need_time, Toast.LENGTH_LONG).show(); return }
        if (TimeFlow.resolve(orderedTimes()).badIndex >= 0) { Toast.makeText(this, R.string.ar_time_order, Toast.LENGTH_LONG).show(); return }

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
            else {
                btnSubmit.isEnabled = true
                val detail = SupabaseClient.lastError.ifBlank { getString(R.string.ar_failed) }
                Toast.makeText(this@AddRouteActivity, "${getString(R.string.ar_failed)}\n$detail", Toast.LENGTH_LONG).show()
            }
        }
    }
}
