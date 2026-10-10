package com.bustime.app.ui.addroute

import android.app.TimePickerDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
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
import com.bustime.app.utils.TimeFormat
import com.bustime.app.utils.TextNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * यूज़र रूट की दिशा (जैसे A→Z या Z→A) चुनता है, पड़ाव अपने-आप आ जाते हैं,
 * वह चलने का समय और जो पड़ावों के समय पता हों भरता है (खाली छोड़ना ठीक है) और भेज देता है।
 * यह सीधे ऐप में नहीं दिखता: पहले एडमिन जाँचता है।
 */
class AddRouteActivity : AppCompatActivity() {

    private class StopRow(val placeId: String, val label: TextView, var time: String? = null)

    private lateinit var repo: AppRepository
    private lateinit var tvStart: TextView
    private lateinit var stopsBox: LinearLayout
    private lateinit var btnSubmit: Button
    private lateinit var cbLongRoute: CheckBox
    private lateinit var tvStartDate: TextView
    private var startDate: String? = null

    private var routes: List<Route> = emptyList()
    private var selected: Route? = null
    private var startTime: String? = null
    private val rows = ArrayList<StopRow>()
    private var customMode = false
    private lateinit var routeSpinner: Spinner
    private lateinit var customPanel: LinearLayout
    private lateinit var customFrom: EditText
    private lateinit var customTo: EditText
    private lateinit var customStopsBox: LinearLayout
    private val customStops = ArrayList<CustomStop>()
    private lateinit var modeButton: Button
    private data class CustomStop(val name: EditText, val time: TextView, var value: String? = null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_route)
        repo = AppRepository.getInstance(this)
        findViewById<Button>(R.id.btnBackAdd).setOnClickListener { finish() }
        tvStart = findViewById(R.id.tvStartTime)
        stopsBox = findViewById(R.id.stopsBox)
        btnSubmit = findViewById(R.id.btnSubmitRoute)
        routeSpinner = findViewById(R.id.spRoute)
        cbLongRoute = findViewById<CheckBox>(R.id.cbLongRoute)
        cbLongRoute.visibility = View.GONE
        tvStartDate = findViewById<TextView>(R.id.tvStartDate)
        tvStartDate.visibility = View.GONE
        findViewById<TextView>(R.id.tvStartDateLabel).visibility = View.GONE

        val content = findViewById<Button>(R.id.btnSubmitRoute).parent as LinearLayout
        modeButton = Button(this).apply { text = getString(R.string.ar_new_route_mode) }
        content.addView(modeButton, content.indexOfChild(routeSpinner) + 1)
        customPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        customFrom = EditText(this).apply { hint = getString(R.string.ar_custom_from); setSingleLine(true) }
        customTo = EditText(this).apply { hint = getString(R.string.ar_custom_to); setSingleLine(true) }
        customStopsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        customPanel.addView(TextView(this).apply { text = getString(R.string.ar_new_route_intro); textSize = 14f; setPadding(0, dp(8), 0, dp(4)) })
        customPanel.addView(customFrom); customPanel.addView(customTo)
        customPanel.addView(TextView(this).apply { text = getString(R.string.ar_custom_stops); textSize = 14f; setPadding(0, dp(8), 0, dp(4)) })
        customPanel.addView(customStopsBox)
        customPanel.addView(Button(this).apply { text = getString(R.string.ar_add_stop); setOnClickListener { addCustomStop() } })
        content.addView(customPanel, content.indexOfChild(tvStart))
        modeButton.setOnClickListener {
            customMode = !customMode
            routeSpinner.visibility = if (customMode) View.GONE else View.VISIBLE
            stopsBox.visibility = if (customMode) View.GONE else View.VISIBLE
            customPanel.visibility = if (customMode) View.VISIBLE else View.GONE
            modeButton.text = if (customMode) getString(R.string.ar_existing_route_mode) else getString(R.string.ar_new_route_mode)
            if (customMode && customStops.isEmpty()) { addCustomStop(); addCustomStop() }
        }
        tvStart.setOnClickListener { pickTime(startTime) { startTime = it; showTime(tvStart, it) } }
        btnSubmit.setOnClickListener { submit() }
        lifecycleScope.launch {
            routes = repo.getAllRoutes().distinctBy { it.fromId + ">" + it.toId }
            val labels = if (routes.isEmpty()) listOf("No existing routes — use new route option above") else {
                val ids = routes.flatMap { listOf(it.fromId, it.toId) }.toSet()
                val names = repo.getPlacesById(ids); val lang = LanguageHelper.current()
                routes.map { "${names[it.fromId]?.displayName(lang) ?: it.fromId} → ${names[it.toId]?.displayName(lang) ?: it.toId}" }
            }
            routeSpinner.adapter = ArrayAdapter(this@AddRouteActivity, android.R.layout.simple_spinner_dropdown_item, labels)
            routeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { routes.getOrNull(pos)?.let { loadStops(it) } }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
            if (routes.isEmpty()) {
                routeSpinner.visibility = View.GONE; customMode = true
                customPanel.visibility = View.VISIBLE; stopsBox.visibility = View.GONE
                modeButton.text = getString(R.string.ar_existing_route_mode)
                addCustomStop(); addCustomStop()
            }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun addCustomStop() {
        val name = EditText(this).apply { hint = getString(R.string.ar_custom_stop_name); setSingleLine(true) }
        val time = TextView(this).apply { text = getString(R.string.ar_tap_time); setPadding(dp(12), dp(10), dp(12), dp(10)); setBackgroundColor(0xFFFFFFFF.toInt()) }
        val row = CustomStop(name, time)
        time.setOnClickListener { pickTime(row.value) { row.value = it; time.text = it ?: getString(R.string.ar_tap_time) } }
        customStopsBox.addView(name); customStopsBox.addView(time); customStops.add(row)
    }

    private fun loadStops(route: Route) {
        selected = route
        lifecycleScope.launch {
            val stops = repo.getRouteStops(route.id)
            val names = repo.getPlacesById(stops.map { it.placeId }.toSet())
            val lang = LanguageHelper.current()
            stopsBox.removeAllViews()
            rows.clear()
            val pad = (10 * resources.displayMetrics.density).toInt()
            // पहला पड़ाव = चलने की जगह (उसका समय ऊपर "चलने का समय" है), इसलिए उसे छोड़ते हैं
            for (s in stops.drop(1)) {
                val line = LinearLayout(this@AddRouteActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, pad, 0, pad)
                }
                val name = TextView(this@AddRouteActivity).apply {
                    text = names[s.placeId]?.displayName(lang) ?: s.placeId
                    textSize = 16f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val time = TextView(this@AddRouteActivity).apply {
                    text = getString(R.string.ar_tap_time)
                    textSize = 15f
                    setBackgroundColor(0xFFFFFFFF.toInt())
                    setPadding(pad, pad, pad, pad)
                }
                val row = StopRow(s.placeId, time)
                time.setOnClickListener {
                    pickTime(row.time) { t ->
                        if (false && t != null && violatesTimeOrder(t, row)) {
                            Toast.makeText(this@AddRouteActivity, "स्टॉप का समय पिछले समय से पहले नहीं हो सकता। Long Route चुनें यदि बस आधी रात पार करती है।", Toast.LENGTH_LONG).show()
                        } else {
                            row.time = t
                            showTime(time, t)
                        }
                    }
                }
                line.addView(name)
                line.addView(time)
                stopsBox.addView(line)
                rows.add(row)
            }
        }
    }

    private fun showTime(tv: TextView, hhmm: String?) {
        tv.text = if (hhmm == null) getString(R.string.ar_tap_time) else TimeFormat.format(hhmm)
    }

    /** समय चुनने का डायलॉग; "हटाएँ" दबाने पर समय खाली हो जाता है */
    private fun pickTime(current: String?, onPicked: (String?) -> Unit) {
        val h = current?.substringBefore(":")?.toIntOrNull() ?: 8
        val m = current?.substringAfter(":", "")?.toIntOrNull() ?: 0
        val dlg = TimePickerDialog(this, { _, hh, mm ->
            onPicked("%02d:%02d".format(hh, mm))
        }, h, m, false)
        dlg.setButton(android.content.DialogInterface.BUTTON_NEUTRAL, getString(R.string.ar_clear)) { _, _ ->
            onPicked(null)
        }
        dlg.show()
    }

    private fun setStartDate(date: Date) {
        startDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
        tvStartDate.text = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)
    }

    private fun violatesTimeOrder(time: String, row: StopRow): Boolean {
        val index = rows.indexOf(row)
        var previous = startTime ?: return false
        for (i in 0 until index) {
            rows[i].time?.let { previous = it }
        }
        return time < previous
    }

    private fun submit() {
        val route = selected
        if (!SupabaseClient.isConfigured()) { Toast.makeText(this, R.string.ar_not_set, Toast.LENGTH_LONG).show(); return }
        val start = startTime
        val phone = findViewById<EditText>(R.id.etPhone).text.toString().filter { it.isDigit() }
        if (phone.length !in 10..13) { Toast.makeText(this, R.string.ar_need_phone, Toast.LENGTH_SHORT).show(); return }
        if (customMode && (customFrom.text.toString().trim().isBlank() || customTo.text.toString().trim().isBlank())) {
            Toast.makeText(this, R.string.ar_custom_need_endpoints, Toast.LENGTH_SHORT).show(); return
        }
        if (!customMode && route == null) { Toast.makeText(this, R.string.ar_new_route_mode, Toast.LENGTH_SHORT).show(); return }
        val stopData = JSONArray()
        var previous = start ?: "00:00"; var dayOffset = 0
        if (customMode) {
            customStops.forEach { row ->
                val name = row.name.text.toString().trim()
                if (name.isNotBlank()) {
                    val t = row.value
                    if (t != null) { if (t < previous) dayOffset++; previous = t }
                    stopData.put(JSONObject().put("place_name", name).put("normalized_name", TextNormalizer.normalize(name)).put("time", t ?: JSONObject.NULL).put("day_offset", dayOffset))
                }
            }
        } else {
            rows.forEach { row ->
                val t = row.time
                if (t != null) { if (t < previous) dayOffset++; previous = t }
                stopData.put(JSONObject().put("place", row.placeId).put("time", t ?: JSONObject.NULL).put("day_offset", dayOffset))
            }
        }
        val anyStopTime = if (customMode) customStops.any { it.name.text.toString().isNotBlank() && it.value != null } else rows.any { it.time != null }
        if (start == null && !anyStopTime) { Toast.makeText(this, R.string.ar_need_start, Toast.LENGTH_SHORT).show(); return }
        val fromText = if (customMode) customFrom.text.toString().trim() else ""
        val toText = if (customMode) customTo.text.toString().trim() else ""
        val body = JSONObject()
            .put("from_id", if (!customMode) route!!.fromId else JSONObject.NULL)
            .put("to_id", if (!customMode) route!!.toId else JSONObject.NULL)
            .put("from_text", fromText.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("to_text", toText.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("from_normalized", if (customMode) TextNormalizer.normalize(fromText) else JSONObject.NULL)
            .put("to_normalized", if (customMode) TextNormalizer.normalize(toText) else JSONObject.NULL)
            .put("source_language", LanguageHelper.current())
            .put("route_text", if (customMode) "$fromText → $toText" else JSONObject.NULL)
            .put("submission_type", if (customMode) "new_route" else "route")
            .put("start_time", start ?: JSONObject.NULL)
            .put("service_name", findViewById<EditText>(R.id.etService).text.toString().trim().takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("stops", stopData).put("phone", phone).put("status", "pending")
        btnSubmit.isEnabled = false
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { SupabaseClient.insertRouteSubmission(body) }
            if (ok) { Toast.makeText(this@AddRouteActivity, R.string.ar_sent, Toast.LENGTH_LONG).show(); finish() }
            else { btnSubmit.isEnabled = true; Toast.makeText(this@AddRouteActivity, R.string.ar_failed, Toast.LENGTH_LONG).show() }
        }
    }

}
