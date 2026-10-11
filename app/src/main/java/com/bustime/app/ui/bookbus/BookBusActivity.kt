package com.bustime.app.ui.bookbus

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bustime.app.ui.community.CommunitySupabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Lists admin-published operators; contact is direct and there is no in-app booking/payment. */
class BookBusActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(com.bustime.app.R.string.book_bus)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 12, 18, 18) }
        root.addView(TextView(this).apply { text = getString(com.bustime.app.R.string.bb_intro); textSize = 15f; setPadding(0, 8, 0, 10) })
        val areaLabel = TextView(this).apply { text = "Select your area (required)"; textSize = 16f; setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 8, 0, 6) }
        root.addView(areaLabel)
        val areaSpinner = android.widget.Spinner(this)
        areaSpinner.isEnabled = false
        root.addView(areaSpinner, LinearLayout.LayoutParams(-1, -2))
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 12, 0, 0) }
        root.addView(results)
        val scroll = ScrollView(this).apply { addView(root) }; setContentView(scroll)
        lifecycleScope.launch {
            val raw = withContext(Dispatchers.IO) { CommunitySupabase.getPublicOperators() }
            if (raw == null) {
                results.addView(TextView(this@BookBusActivity).apply { text = getString(com.bustime.app.R.string.bb_load_failed) })
                root.addView(TextView(this@BookBusActivity).apply { text = getString(com.bustime.app.R.string.bb_load_failed) })
                results.addView(com.google.android.material.button.MaterialButton(this@BookBusActivity).apply {
                    text = "Add Your Bus"
                    setOnClickListener { startActivity(Intent(this@BookBusActivity, com.bustime.app.ui.community.CommunityRequestActivity::class.java).putExtra("mode", "bus_operator")) }
                })
                return@launch
            }
            val rows = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
            val allRows = (0 until rows.length()).map { rows.getJSONObject(it) }
            val areas = allRows.map { it.optString("location").trim() }.filter { it.isNotBlank() }.distinct().sorted()
            areaSpinner.adapter = android.widget.ArrayAdapter(this@BookBusActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Choose an area") + areas)
            areaSpinner.isEnabled = areas.isNotEmpty()
            if (areas.isEmpty()) results.addView(TextView(this@BookBusActivity).apply { text = "No service areas are available yet. Admin must add an operating area to each operator." })
            fun renderArea(selectedArea: String) {
                results.removeAllViews()
                if (selectedArea.isBlank()) {
                    results.addView(TextView(this@BookBusActivity).apply { text = "Select an area above to view matching bus options."; textSize = 15f; setPadding(0, 8, 0, 8) })
                    return
                }
                val matching = allRows.filter { it.optString("location").trim().equals(selectedArea, ignoreCase = true) }
                if (matching.isEmpty()) results.addView(TextView(this@BookBusActivity).apply { text = "No bus options available in $selectedArea."; textSize = 16f })
                for (item in matching) {
                val card = LinearLayout(this@BookBusActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(14, 14, 14, 14); setBackgroundColor(getColor(com.bustime.app.R.color.surfaceVariant)); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 } }
                val name = item.optString("operator_name", "Bus Operator")
                card.addView(TextView(this@BookBusActivity).apply { text = name; textSize = 19f; setTypeface(null, android.graphics.Typeface.BOLD) })
                val routeSummary = "${item.optString("from_text", "-")} → ${item.optString("to_text", "-")}"
                val busType = item.optString("bus_type", "Bus")
                val seatType = item.optString("seat_type", "").ifBlank { getString(com.bustime.app.R.string.bb_seat_unspecified) }
                val capacity = listOfNotNull(
                    if (!item.isNull("sleeper_count")) item.optInt("sleeper_count").takeIf { it >= 0 }?.let { "$it sleeper berths" } else null,
                    if (!item.isNull("seater_count")) item.optInt("seater_count").takeIf { it >= 0 }?.let { "$it seats" } else null
                ).joinToString(" · ")
                card.addView(TextView(this@BookBusActivity).apply { text = "${item.optString("service_name", getString(com.bustime.app.R.string.bb_service))} · $busType · $seatType" + if (capacity.isNotBlank()) " · $capacity" else ""; textSize = 15f; setPadding(0, 6, 0, 3) })
                card.addView(TextView(this@BookBusActivity).apply { text = "${getString(com.bustime.app.R.string.bb_route)}: $routeSummary"; textSize = 14f; setPadding(0, 2, 0, 5) })
                val detailLines = listOf("Service: ${item.optString("service_name", "-")}", "Bus type: $busType", "Seat type: $seatType", "${getString(com.bustime.app.R.string.bb_route)}: $routeSummary", "${getString(com.bustime.app.R.string.bb_location)}: ${item.optString("location", "-" )}", item.optString("details", "")).filter { it.isNotBlank() }
                val detailBox = LinearLayout(this@BookBusActivity).apply { orientation = LinearLayout.VERTICAL; visibility = android.view.View.GONE }
                detailLines.forEach { line -> detailBox.addView(TextView(this@BookBusActivity).apply { text = line; textSize = 14f; setPadding(0, 5, 0, 0) }) }
                card.addView(TextView(this@BookBusActivity).apply { text = getString(com.bustime.app.R.string.bb_expand); textSize = 13f; setPadding(0, 8, 0, 4); setOnClickListener { detailBox.visibility = if (detailBox.visibility == android.view.View.VISIBLE) android.view.View.GONE else android.view.View.VISIBLE } })
                card.addView(detailBox)
                val phone = item.optString("phone", "").filter { it.isDigit() || it == '+' }
                if (phone.isNotBlank()) card.addView(TextView(this@BookBusActivity).apply {
                    text = "☎  ${getString(com.bustime.app.R.string.bb_contact)}: $phone"; textSize = 16f; setTextColor(getColor(com.bustime.app.R.color.colorPrimary)); gravity = Gravity.CENTER_VERTICAL; setPadding(0, 12, 0, 2)
                    setOnClickListener { try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) } catch (_: Exception) { Toast.makeText(this@BookBusActivity, com.bustime.app.R.string.bb_call_error, Toast.LENGTH_SHORT).show() } }
                })
                val whatsapp = item.optString("whatsapp", phone).filter { it.isDigit() || it == '+' }
                if (whatsapp.isNotBlank()) card.addView(TextView(this@BookBusActivity).apply {
                    text = "${getString(com.bustime.app.R.string.bb_whatsapp)}: $whatsapp"; textSize = 16f; setTextColor(0xFF128C7E.toInt()); setPadding(0, 8, 0, 2)
                    setOnClickListener { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${whatsapp.filter { c -> c.isDigit() }}"))) } catch (_: Exception) { Toast.makeText(this@BookBusActivity, com.bustime.app.R.string.bb_whatsapp_error, Toast.LENGTH_SHORT).show() } }
                })
                results.addView(card)
                }
            }
            areaSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    renderArea(if (position == 0) "" else areas[position - 1])
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) { renderArea("") }
            }
            renderArea("")
            // Keep Add Your Bus outside results because renderArea() clears results on each area selection.
            val addBus = com.google.android.material.button.MaterialButton(this@BookBusActivity).apply {
                text = "Add Your Bus"
                setOnClickListener {
                    startActivity(Intent(this@BookBusActivity, com.bustime.app.ui.community.CommunityRequestActivity::class.java).putExtra("mode", "bus_operator"))
                }
            }
            root.addView(addBus, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 16 })
        }
    }
}
