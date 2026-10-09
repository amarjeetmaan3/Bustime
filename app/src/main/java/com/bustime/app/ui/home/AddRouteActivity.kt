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
import com.bustime.app.utils.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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

    private var routes: List<Route> = emptyList()
    private var selected: Route? = null
    private var startTime: String? = null
    private val rows = ArrayList<StopRow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_route)
        repo = AppRepository.getInstance(this)

        findViewById<Button>(R.id.btnBackAdd).setOnClickListener { finish() }
        tvStart = findViewById(R.id.tvStartTime)
        stopsBox = findViewById(R.id.stopsBox)
        btnSubmit = findViewById(R.id.btnSubmitRoute)

        tvStart.setOnClickListener {
            pickTime(startTime) { startTime = it; showTime(tvStart, it) }
        }
        btnSubmit.setOnClickListener { submit() }

        lifecycleScope.launch {
            val all = repo.getAllRoutes()
            // एक दिशा (From→To) की एक ही एंट्री दिखाओ
            routes = all.distinctBy { it.fromId + ">" + it.toId }
            if (routes.isEmpty()) {
                Toast.makeText(this@AddRouteActivity, R.string.ar_no_routes, Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            val ids = routes.flatMap { listOf(it.fromId, it.toId) }.toSet()
            val names = repo.getPlacesById(ids)
            val lang = LanguageHelper.current()
            fun nm(id: String) = names[id]?.displayName(lang) ?: id
            val labels = routes.map { "${nm(it.fromId)} → ${nm(it.toId)}" }

            val sp = findViewById<Spinner>(R.id.spRoute)
            sp.adapter = ArrayAdapter(this@AddRouteActivity, android.R.layout.simple_spinner_dropdown_item, labels)
            sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    loadStops(routes[pos])
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
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
                    pickTime(row.time) { t -> row.time = t; showTime(time, t) }
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

    private fun submit() {
        val route = selected ?: return
        if (!SupabaseClient.isConfigured()) {
            Toast.makeText(this, R.string.ar_not_set, Toast.LENGTH_LONG).show()
            return
        }
        val start = startTime
        if (start == null) {
            Toast.makeText(this, R.string.ar_need_start, Toast.LENGTH_SHORT).show()
            return
        }
        val phone = findViewById<EditText>(R.id.etPhone).text.toString().filter { it.isDigit() }
        if (phone.length < 10 || phone.length > 13) {
            Toast.makeText(this, R.string.ar_need_phone, Toast.LENGTH_SHORT).show()
            return
        }
        val stopsJson = JSONArray()
        for (r in rows) {
            stopsJson.put(JSONObject().put("place", r.placeId).put("time", r.time ?: JSONObject.NULL))
        }
        val body = JSONObject()
            .put("from_id", route.fromId)
            .put("to_id", route.toId)
            .put("start_time", start)
            .put("service_name", findViewById<EditText>(R.id.etService).text.toString().trim().ifEmpty { null } ?: JSONObject.NULL)
            .put("stops", stopsJson)
            .put("phone", phone)

        btnSubmit.isEnabled = false
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { SupabaseClient.insertRouteSubmission(body) }
            if (ok) {
                Toast.makeText(this@AddRouteActivity, R.string.ar_sent, Toast.LENGTH_LONG).show()
                finish()
            } else {
                btnSubmit.isEnabled = true
                Toast.makeText(this@AddRouteActivity, R.string.ar_failed, Toast.LENGTH_LONG).show()
            }
        }
    }
}
