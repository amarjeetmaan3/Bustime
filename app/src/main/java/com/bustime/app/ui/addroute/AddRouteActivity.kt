package com.bustime.app.ui.addroute

import android.os.Bundle
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
import com.bustime.app.ui.common.StopListEditor
import com.bustime.app.utils.LanguageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * ऊपर दो विकल्प:
 *  Existing Route = पहले से बने रूट की कॉपी (रूट चुनो, पड़ाव/समय बदलो)
 *  New Route      = बिल्कुल नया रूट (दो खाली पड़ाव से शुरू, किसी भी भाषा में नाम)
 * पड़ावों की लिस्ट Route Correction वाली ही है (StopListEditor)। समय का क्रम TimeFlow से जाँचा जाता है।
 * सब कुछ पहले एडमिन के रिव्यू में जाता है।
 */
class AddRouteActivity : AppCompatActivity() {
    private lateinit var repo: AppRepository
    private lateinit var stopsBox: LinearLayout
    private lateinit var btnSubmit: Button
    private var editor: StopListEditor? = null
    private val drafts = mutableListOf<JSONObject>()
    private var isNew = true
    private var routes: List<Route> = emptyList()
    private var selected: Route? = null

    private fun blankDraft(): JSONObject = JSONObject().put("place_id", JSONObject.NULL).put("name", "")
        .put("language", LanguageHelper.current()).put("time", JSONObject.NULL)

    private fun timeOf(d: JSONObject): String? =
        if (d.isNull("time")) null else d.optString("time").trim().takeIf { it.isNotBlank() && it != "null" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_route)
        repo = AppRepository.getInstance(this)
        stopsBox = findViewById(R.id.stopsBox)
        btnSubmit = findViewById(R.id.btnSubmitRoute)
        val spinner = findViewById<Spinner>(R.id.spRoute)

        setMode(true)
        findViewById<Button>(R.id.btnUseExistingRoute).setOnClickListener {
            setMode(false)
            loadSelectedRoute()
        }
        findViewById<Button>(R.id.btnUseNewRoute).setOnClickListener { setMode(true) }
        btnSubmit.setOnClickListener { submit() }

        lifecycleScope.launch {
            val places = withContext(Dispatchers.IO) { repo.getAllPlaces() }
            editor = StopListEditor(
                this@AddRouteActivity, stopsBox, places, drafts,
                minStops = 2
            
            )
            editor?.render()

            val allRoutes = repo.getAllRoutes()
            val stopsByRoute = repo.getAllRouteStops()
                .groupBy { it.routeId }
                .mapValues { (_, stops) -> stops.sortedBy { it.stopOrder }.map { it.placeId } }

            // Existing Route is a stop-copy picker, not a list of individual bus services.
            // Routes with the same complete ordered stop sequence appear only once.
            routes = allRoutes.distinctBy { route ->
                stopsByRoute[route.id].orEmpty().ifEmpty { listOf(route.fromId, route.toId) }
            }

            val ids = routes.flatMap { route ->
                stopsByRoute[route.id].orEmpty().ifEmpty { listOf(route.fromId, route.toId) }
            }.toSet()
            val names = repo.getPlacesById(ids)
            val lang = LanguageHelper.current()
            // Keep the picker focused on the main From → To route, not a long village chain.
            // If multiple variants share the same endpoints, add a short service/route hint to distinguish them.
            fun placeName(placeId: String): String =
                names[placeId]?.displayName(lang)?.takeIf { it.isNotBlank() } ?: placeId
            val endpointKeys = routes.map { "${it.fromId}→${it.toId}" }
            val endpointCounts = endpointKeys.groupingBy { it }.eachCount()
            val labels = routes.mapIndexed { index, route ->
                val mainLabel = "${placeName(route.fromId)} → ${placeName(route.toId)}"
                if ((endpointCounts[endpointKeys[index]] ?: 0) > 1) {
                    val variant = (route.routeName ?: route.serviceName)?.takeIf { it.isNotBlank() } ?: route.id
                    "$mainLabel · $variant"
                } else mainLabel
            }
            spinner.adapter = ArrayAdapter(this@AddRouteActivity, android.R.layout.simple_spinner_dropdown_item, labels)
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (!isNew && position in routes.indices) loadSelectedRoute()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
    }

    private fun setMode(newMode: Boolean) {
        val wasNew = isNew
        isNew = newMode
        if (newMode) {
            // साफ़ नया ड्राफ़्ट: पुराने कॉपी किए रूट का कोई हिस्सा नहीं बचता
            if (!wasNew || drafts.isEmpty()) {
                selected = null
                drafts.clear()
                drafts.add(blankDraft())
                drafts.add(blankDraft())
                editor?.render()
            }
        }
        findViewById<View>(R.id.layoutExisting).visibility = if (newMode) View.GONE else View.VISIBLE
        findViewById<View>(R.id.btnUseExistingRoute).visibility = if (newMode) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.tvModeHint).setText(if (newMode) R.string.ar_new_route_intro else R.string.ar_existing_route_intro)
    }

    private fun loadSelectedRoute() {
        val spinner = findViewById<Spinner>(R.id.spRoute)
        val route = routes.getOrNull(spinner.selectedItemPosition) ?: return
        lifecycleScope.launch {
            val stops = repo.getRouteStops(route.id).sortedBy { it.stopOrder }
            val names = repo.getPlacesById(stops.map { it.placeId }.toSet())
            val lang = LanguageHelper.current()
            if (isNew) return@launch
            selected = route
            drafts.clear()
            stops.forEach { stop ->
                drafts.add(JSONObject().put("place_id", stop.placeId)
                    .put("name", names[stop.placeId]?.displayName(lang) ?: stop.placeId)
                    .put("language", lang)
                    .put("time", stop.time ?: JSONObject.NULL))
            }
            editor?.render()
        }
    }

    private fun submit() {
        if (!SupabaseClient.isConfigured()) { Toast.makeText(this, R.string.ar_not_set, Toast.LENGTH_LONG).show(); return }
        if (!isNew && selected == null) { Toast.makeText(this, R.string.ar_pick_route, Toast.LENGTH_SHORT).show(); return }
        val phone = findViewById<EditText>(R.id.etPhone).text.toString().filter { it.isDigit() }
        if (phone.length !in 10..13) { Toast.makeText(this, R.string.ar_need_phone, Toast.LENGTH_SHORT).show(); return }
        val service = findViewById<EditText>(R.id.etService).text.toString().trim()
        if (drafts.size < 2) { Toast.makeText(this, R.string.ar_min_stops, Toast.LENGTH_SHORT).show(); return }
        val duplicate = editor?.duplicateStopIndexes()
        if (duplicate != null) {
            val message = when (LanguageHelper.current()) {
                "hi" -> "स्टॉप ${duplicate.first} और ${duplicate.second} एक ही जगह हैं। हर स्टॉप केवल एक बार रखें।"
                "pa" -> "ਸਟਾਪ ${duplicate.first} ਅਤੇ ${duplicate.second} ਇੱਕੋ ਥਾਂ ਹਨ। ਹਰ ਸਟਾਪ ਸਿਰਫ਼ ਇੱਕ ਵਾਰ ਰੱਖੋ।"
                else -> "Stops ${duplicate.first} and ${duplicate.second} refer to the same place. Each stop can appear only once."
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            return
        }

        val stops = JSONArray()
        for (d in drafts) {
            val nm = d.optString("name").trim()
            if (nm.isBlank()) { Toast.makeText(this, R.string.ar_stop_name_required, Toast.LENGTH_SHORT).show(); return }
            stops.put(JSONObject()
                .put("place", if (d.isNull("place_id")) JSONObject.NULL else d.optString("place_id"))
                .put("name", nm)
                .put("time", timeOf(d) ?: JSONObject.NULL))
        }

        val first = drafts.first()
        val last = drafts.last()
        val fromText = first.optString("name").trim()
        val toText = last.optString("name").trim()
        val body = JSONObject()
            .put("submission_type", "new_route")
            .put("from_id", if (first.isNull("place_id")) JSONObject.NULL else first.optString("place_id"))
            .put("to_id", if (last.isNull("place_id")) JSONObject.NULL else last.optString("place_id"))
            .put("from_text", fromText)
            .put("to_text", toText)
            .put("route_text", "$fromText → $toText")
            .put("start_time", timeOf(first) ?: JSONObject.NULL)
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
