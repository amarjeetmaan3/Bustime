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
        title = when (mode) { "correction" -> getString(com.bustime.app.R.string.community_title_correction); "vehicle" -> getString(com.bustime.app.R.string.community_title_vehicle); "bus_operator" -> getString(com.bustime.app.R.string.community_title_bus); "vehicle_correction" -> if (intent.getStringExtra("vehicle_type") == "taxi") getString(com.bustime.app.R.string.community_title_taxi_correction) else getString(com.bustime.app.R.string.community_title_auto_correction); else -> getString(com.bustime.app.R.string.community_title_feedback) }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(24)) }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        if (mode == "correction" || mode == "bus_operator") text(title, 18f)\n        else text("${title}\n${getString(com.bustime.app.R.string.community_review_notice)}", 18f)
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
            placeNames = allPlaces.associate { it.id to it.displayName(LanguageHelper.current()) }

            // The route is passed from the selected route's details page; keep it fixed here.
            val requested = intent.getStringExtra("route_id")
            val route = routes.firstOrNull { it.id == requested }
            if (route == null) {
                text(getString(com.bustime.app.R.string.community_open_route_details), 14f)
                return@launch
            }

            heading(getString(com.bustime.app.R.string.community_route_label, routeLabel(route)))
            text(getString(com.bustime.app.R.string.community_complete_route_correction), 16f, true)
            text(getString(com.bustime.app.R.string.community_complete_route_help), 14f)
            correctionEditor = LinearLayout(this@CommunityRequestActivity).apply { orientation = LinearLayout.VERTICAL }
            root.addView(correctionEditor!!)
            field("correction_note", getString(com.bustime.app.R.string.community_extra_admin_note))
            field("phone", getString(com.bustime.app.R.string.community_mobile_required))
            button(getString(com.bustime.app.R.string.community_submit_route_correction)) { submitCorrection() }
            loadStopsFor(route)
        }
    }

    private suspend fun loadStopsFor(route: Route) {
        correctionRoute = route
        routeStops = AppRepository.getInstance(this).getRouteStops(route.id).sortedBy { it.stopOrder }
        placeNames = allPlaces.associate { it.id to it.displayName(LanguageHelper.current()) }
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
            text = getString(com.bustime.app.R.string.community_add_start)
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
                hint = if (index == 0) getString(com.bustime.app.R.string.community_start_stop) else if (index == correctionDrafts.lastIndex) getString(com.bustime.app.R.string.community_destination) else getString(com.bustime.app.R.string.community_stop_name)
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
                text = if (t.isBlank()) "＋ ${getString(com.bustime.app.R.string.ar_tap_time)}" else t
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
                contentDescription = getString(com.bustime.app.R.string.ar_add_stop_below)
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
                contentDescription = getString(com.bustime.app.R.string.ar_more_stop_actions)
                setOnClickListener { anchor ->
                    draft.put("name", name.text.toString().trim())
                    val menu = android.widget.PopupMenu(this@CommunityRequestActivity, anchor)
                    menu.menu.add(getString(com.bustime.app.R.string.ar_move_up)).isEnabled = index > 0
                    menu.menu.add(getString(com.bustime.app.R.string.ar_move_down)).isEnabled = index < correctionDrafts.lastIndex
                    menu.menu.add(getString(com.bustime.app.R.string.ar_remove_stop)).setIcon(android.R.drawable.ic_menu_delete)
                    menu.setOnMenuItemClickListener { item ->
                        when (item.title.toString()) {
                            getString(com.bustime.app.R.string.ar_move_up) -> if (index > 0) { val v = correctionDrafts.removeAt(index); correctionDrafts.add(index - 1, v); renderCorrectionList() }
                            getString(com.bustime.app.R.string.ar_move_down) -> if (index < correctionDrafts.lastIndex) { val v = correctionDrafts.removeAt(index); correctionDrafts.add(index + 1, v); renderCorrectionList() }
                            getString(com.bustime.app.R.string.ar_remove_stop) -> android.app.AlertDialog.Builder(this@CommunityRequestActivity)
                                .setTitle(getString(com.bustime.app.R.string.community_remove_stop_question))
                                .setMessage(getString(com.bustime.app.R.string.community_remove_stop_warning))
                                .setNegativeButton(getString(com.bustime.app.R.string.cancel), null)
                                .setPositiveButton(getString(com.bustime.app.R.string.remove)) { _, _ -> correctionDrafts.removeAt(index); renderCorrectionList() }.show()
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
            text = getString(com.bustime.app.R.string.community_add_stop_end)
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
        if (phone.length !in 10..13) { toast(getString(com.bustime.app.R.string.community_enter_valid_mobile)); return }
        if (correctionDrafts.size < 2) { toast(getString(com.bustime.app.R.string.community_route_two_stops)); return }
        val proposed = org.json.JSONArray()
        val seen = mutableMapOf<String, Int>()
        correctionDrafts.forEachIndexed { index, d ->
            val name = d.optString("name").trim()
            if (name.isBlank()) { toast(getString(com.bustime.app.R.string.community_enter_stop_name, index + 1)); return }
            // JSONObject.NULL can stringify to "null"; treat it as an empty optional time.
            val t = if (d.isNull("time")) "" else d.optString("time").trim().takeUnless { it == "null" }.orEmpty()
            if (t.isNotBlank() && !t.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$"))) { toast(getString(com.bustime.app.R.string.community_stop_invalid_time, index + 1)); return }
            var id = if (d.isNull("place_id")) "" else d.optString("place_id")
            // Resolve a manually typed alias to the canonical place record, regardless
            // of whether the user typed its English, Hindi, or Punjabi name.
            val normalizedName = name.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
            val matchingPlace = allPlaces.firstOrNull { place ->
                place.names.values.any { alias ->
                    alias.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ") == normalizedName
                }
            }
            if (id.isBlank() && matchingPlace != null) id = matchingPlace.id
            val key = id.ifBlank { normalizedName }
            seen[key] = (seen[key] ?: 0) + 1
            proposed.put(JSONObject().put("place_id", id.ifBlank { JSONObject.NULL }).put("name", name)
                .put("language", d.optString("language", LanguageHelper.current()))
                .put("time", t.ifBlank { JSONObject.NULL }).put("position", index))
        }
        if (seen.values.any { it > 1 }) {
            toast(getString(com.bustime.app.R.string.community_duplicate_stop))
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
        sendRequests(listOf(body), "correction_submissions", getString(com.bustime.app.R.string.community_route_correction_sent))
    }

    private fun vehicleForm() {
        val requestedType = intent.getStringExtra("vehicle_type")
        val typeSpinner = spinner(getString(com.bustime.app.R.string.community_vehicle_type), listOf(getString(com.bustime.app.R.string.community_auto), getString(com.bustime.app.R.string.community_taxi)), "vehicle_type")
        if (requestedType == "taxi") typeSpinner.setSelection(1)
        if (requestedType == "auto" || requestedType == "taxi") typeSpinner.visibility = View.GONE
        field("person_name", getString(com.bustime.app.R.string.community_driver_name))
        field("vehicle_name", getString(com.bustime.app.R.string.community_vehicle_name))
        field("vehicle_model_number", getString(com.bustime.app.R.string.community_vehicle_model))
        field("seats", getString(com.bustime.app.R.string.community_seat_count))
        field("vehicle_number", getString(com.bustime.app.R.string.community_vehicle_registration))
        field("location", getString(com.bustime.app.R.string.community_location_area))
        field("union_name", getString(com.bustime.app.R.string.community_union_name))
        field("phone", getString(com.bustime.app.R.string.community_mobile_number))
        val fuelSpinner = spinner(getString(com.bustime.app.R.string.community_auto_fuel_type), listOf("EV", getString(com.bustime.app.R.string.fuel_oil), "CNG", getString(com.bustime.app.R.string.community_other)), "fuel_type")
        if (requestedType != "auto") fuelSpinner.visibility = View.GONE
        field("details", getString(com.bustime.app.R.string.community_other_info)).apply { minLines = 2; maxLines = 4; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE }
        button(getString(com.bustime.app.R.string.community_submit_listing)) {
            val type = if (requestedType == "auto" || (requestedType == null && spinners["vehicle_type"]?.selectedItemPosition == 0)) "auto" else "taxi"
            val name = value("person_name"); val vehicle = value("vehicle_name"); val phone = digits(value("phone"))
            val seats = value("seats").toIntOrNull()
            val area = value("location")
            if (name.isBlank() || vehicle.isBlank() || value("vehicle_model_number").isBlank() || value("vehicle_number").isBlank() ||
                area.isBlank() || value("union_name").isBlank() || phone.length !in 10..13 || seats == null || seats < 1) {
                toast(getString(com.bustime.app.R.string.community_fill_vehicle_required)); return@button
            }
            val body = JSONObject().put("vehicle_type", type).put("person_name", name).put("vehicle_name", vehicle)
                .put("vehicle_model_number", value("vehicle_model_number")).put("vehicle_number", value("vehicle_number"))
                .put("seats", seats).put("location", area).put("area_name", area)
                .put("union_name", value("union_name")).put("phone", phone)
                .put("fuel_type", if (type == "auto") listOf("ev", "oil", "cng", "other").getOrNull(spinners["fuel_type"]?.selectedItemPosition ?: -1) else JSONObject.NULL)
                .put("details", value("details").takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            sendRequests(listOf(body), "vehicle_submissions", getString(com.bustime.app.R.string.community_listing_sent))
        }
    }

    private fun busCategoryLabel(value: String): String = when (value.trim().lowercase(Locale.ROOT)) {
        "ordinary" -> getString(com.bustime.app.R.string.bus_category_ordinary)
        "luxury" -> getString(com.bustime.app.R.string.bus_category_luxury)
        "sleeper" -> getString(com.bustime.app.R.string.bus_category_sleeper)
        "sleeper plus" -> getString(com.bustime.app.R.string.bus_category_sleeper_plus)
        "seater plus" -> getString(com.bustime.app.R.string.bus_category_seater_plus)
        "seater" -> getString(com.bustime.app.R.string.bus_category_seater)
        "sleeper + seater" -> getString(com.bustime.app.R.string.bus_category_sleeper_seater)
        "custom" -> getString(com.bustime.app.R.string.bus_category_custom)
        else -> value
    }

    private fun busOperatorForm() {
        heading(getString(com.bustime.app.R.string.community_title_bus))
        text(getString(com.bustime.app.R.string.community_bus_form_intro), 14f)
        field("service_name", getString(com.bustime.app.R.string.community_service_name))
        field("operator_name", getString(com.bustime.app.R.string.community_bus_owner))
        val defaultCategories = listOf("Ordinary", "Luxury", "Sleeper", "Sleeper Plus", "Seater Plus", "Seater", "Sleeper + Seater", "Custom")
        var categoryValues = defaultCategories
        val categorySpinner = spinner(getString(com.bustime.app.R.string.community_bus_category), defaultCategories.map(::busCategoryLabel), "bus_type")
        lifecycleScope.launch {
            val categories = withContext(Dispatchers.IO) { CommunitySupabase.getBusCategories() }
            if (categories.isNotEmpty()) {
                val selected = categoryValues.getOrNull(categorySpinner.selectedItemPosition)
                categoryValues = categories
                categorySpinner.adapter = ArrayAdapter(this@CommunityRequestActivity, android.R.layout.simple_spinner_dropdown_item, categories.map(::busCategoryLabel))
                val index = categories.indexOf(selected)
                if (index >= 0) categorySpinner.setSelection(index)
            }
        }
        field("custom_category", getString(com.bustime.app.R.string.community_custom_bus_type)).apply { minLines = 2; maxLines = 4; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE }
        field("sleeper_count", getString(com.bustime.app.R.string.community_sleeper_berths))
        field("seater_count", getString(com.bustime.app.R.string.community_seater_seats))
        field("phone", getString(com.bustime.app.R.string.community_operator_mobile))
        field("whatsapp", getString(com.bustime.app.R.string.community_whatsapp_optional))
        field("details", getString(com.bustime.app.R.string.community_other_info)).apply { minLines = 2; maxLines = 4; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE }
        button(getString(com.bustime.app.R.string.community_add_bus_submit)) {
            val operator = value("operator_name")
            val service = value("service_name")
            val phone = digits(value("phone"))
            val category = categoryValues.getOrNull(spinners["bus_type"]?.selectedItemPosition ?: -1).orEmpty()
            val variant = value("custom_category")
            val sleeper = value("sleeper_count").toIntOrNull()
            val seater = value("seater_count").toIntOrNull()
            if (service.isBlank() || operator.isBlank() || phone.length !in 10..13 || category.isBlank() || variant.isBlank() ||
                sleeper == null || sleeper < 0 || seater == null || seater < 0 ) {
                toast(getString(com.bustime.app.R.string.community_fill_bus_required)); return@button
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
            sendRequests(listOf(body), "bus_operator_submissions", getString(com.bustime.app.R.string.community_bus_submitted))
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
        text(getString(com.bustime.app.R.string.community_correction_intro, type.uppercase(Locale.ROOT)), 14f)
        text(getString(com.bustime.app.R.string.community_driver_vehicle, driverName, driverVehicle), 16f, true)
        field("correction_name", getString(com.bustime.app.R.string.community_correction_driver_name)).setText(driverName)
        field("correction_vehicle", getString(com.bustime.app.R.string.community_correction_vehicle)).setText(driverVehicle)
        field("correction_phone", getString(com.bustime.app.R.string.community_correct_mobile)).setText(driverPhone)
        if (type == "taxi") field("correction_seats", getString(com.bustime.app.R.string.community_correct_seats)).setText(if (driverSeats > 0) driverSeats.toString() else "")
        else field("correction_fuel", getString(com.bustime.app.R.string.community_correct_fuel)).setText(driverFuel)
        field("correction_info", getString(com.bustime.app.R.string.community_correct_other)).setText(driverInfo)
        field("correction_note", getString(com.bustime.app.R.string.community_explain_correction))
        button(getString(com.bustime.app.R.string.community_send_correction)) {
            val name = value("correction_name")
            val vehicle = value("correction_vehicle")
            val phone = digits(value("correction_phone"))
            val detail = value("correction_note")
            if (name.isBlank() || vehicle.isBlank() || phone.length !in 10..13 || detail.length < 4) {
                toast(getString(com.bustime.app.R.string.community_corrected_details_required)); return@button
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
            sendRequests(listOf(body), "feedback_submissions", getString(com.bustime.app.R.string.community_vehicle_correction_sent))
        }
    }

    private fun feedbackForm() {
        spinner(getString(com.bustime.app.R.string.community_feedback_category), listOf(getString(com.bustime.app.R.string.community_suggestion), getString(com.bustime.app.R.string.community_incorrect_info), getString(com.bustime.app.R.string.community_app_problem), getString(com.bustime.app.R.string.community_other)), "feedback_category")
        field("related_route", getString(com.bustime.app.R.string.community_related_route))
        field("feedback_text", getString(com.bustime.app.R.string.community_describe_feedback))
        field("contact", getString(com.bustime.app.R.string.community_contact_optional))
        button(getString(com.bustime.app.R.string.community_send_feedback)) {
            val message = value("feedback_text")
            if (message.length < 4) { toast(getString(com.bustime.app.R.string.community_please_feedback)); return@button }
            val feedbackCategories = listOf("Suggestion", "Incorrect information", "App problem", "Other")
            val body = JSONObject().put("category", feedbackCategories.getOrNull(spinners["feedback_category"]?.selectedItemPosition ?: -1) ?: "Other")
                .put("related_route", value("related_route").takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                .put("message", message).put("contact", digits(value("contact")).takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            sendRequests(listOf(body), "feedback_submissions", getString(com.bustime.app.R.string.community_thanks_feedback))
        }
    }

    private fun sendRequests(bodies: List<JSONObject>, table: String, success: String) {
        if (!CommunitySupabase.isConfigured()) { toast(getString(com.bustime.app.R.string.community_online_not_configured)); return }
        root.isEnabled = false
        lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) { bodies.map { CommunitySupabase.insert(table, it) } }
            root.isEnabled = true
            if (results.all { it }) { toast(success); finish() }
            else toast(getString(com.bustime.app.R.string.community_send_failed) + CommunitySupabase.lastError)
        }
    }


    private fun addMissingPlaceField(target: LinearLayout) {
        val field = AutoCompleteTextView(this).apply {
            hint = getString(com.bustime.app.R.string.community_missing_stop_name)
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
        val s = Spinner(this); s.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, if (options.isEmpty()) listOf(getString(com.bustime.app.R.string.community_loading_stops)) else options)
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

    private fun routeLabel(route: Route): String {
        fun label(id: String): String = (placeNames[id] ?: id)
            .removeSuffix("_stand")
            .replace('_', ' ')
        return "${label(route.fromId)} → ${label(route.toId)}"
    }
    private fun heading(s: String) { text(s, 16f, true) }
    private fun text(s: String, size: Float, bold: Boolean = false) { root.addView(TextView(this).apply { text = s; textSize = size; if (bold) setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, dp(10), 0, dp(5)) }) }
    private fun field(key: String, hint: String): EditText { val e = EditText(this).apply { this.hint = hint; textSize = 16f; setPadding(dp(12), dp(10), dp(12), dp(10)) }; fields[key] = e; root.addView(e); return e }
    private fun spinner(hint: String, options: List<String>, key: String? = null): Spinner {
        val s = Spinner(this); s.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, if (options.isEmpty()) listOf(getString(com.bustime.app.R.string.community_loading_stops)) else options)
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
