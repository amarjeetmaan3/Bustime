package com.bustime.app.ui.bookbus

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.bustime.app.ui.community.CommunitySupabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Lists admin-published operators; contact is direct and there is no in-app booking/payment. */
class BookBusActivity : AppCompatActivity() {
    private fun localizedCategory(value: String): String = when (value.trim().lowercase(java.util.Locale.ROOT)) {
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

    private fun localizedSeatType(value: String): String = when (value.trim().lowercase(java.util.Locale.ROOT)) {
        "sleeper + seater" -> getString(com.bustime.app.R.string.bus_category_sleeper_seater)
        "sleeper" -> getString(com.bustime.app.R.string.bus_category_sleeper)
        "seater" -> getString(com.bustime.app.R.string.bus_category_seater)
        "not specified" -> getString(com.bustime.app.R.string.bb_not_specified)
        else -> value
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(com.bustime.app.R.string.book_bus)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 12, 18, 0) }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, 16) }
        val scroll = ScrollView(this).apply { addView(content) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val addBus = com.google.android.material.button.MaterialButton(this).apply {
            text = getString(com.bustime.app.R.string.bb_add_your_bus)
            setOnClickListener {
                startActivity(Intent(this@BookBusActivity, com.bustime.app.ui.community.CommunityRequestActivity::class.java).putExtra("mode", "bus_operator"))
            }
        }
        root.addView(addBus, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10; bottomMargin = 12 })
        setContentView(root)
        content.addView(TextView(this).apply { text = getString(com.bustime.app.R.string.bb_intro); textSize = 15f; setPadding(0, 8, 0, 10) })
        var selectedArea: String? = null
        var allRowsCache: List<org.json.JSONObject> = emptyList()
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 12, 0, 0) }
        val areaButton = Button(this).apply {
            text = "Permanent Area: Select Area ▾"
            setOnClickListener {
                val areas = allRowsCache.mapNotNull { row ->
                    row.optString("area_name").ifBlank { row.optString("location") }.trim().takeIf { it.isNotBlank() }
                }.distinctBy { it.lowercase(java.util.Locale.ROOT) }
                val choices = listOf("All Areas") + areas
                AlertDialog.Builder(this@BookBusActivity)
                    .setTitle("Permanent Area")
                    .setSingleChoiceItems(choices.toTypedArray(), (selectedArea?.let { areas.indexOf(it) + 1 } ?: 0)) { dialog, which ->
                        selectedArea = if (which == 0) null else areas[which - 1]
                        text = "Permanent Area: " + (selectedArea ?: "All Areas") + " ▾"
                        dialog.dismiss()
                        renderOperators(allRowsCache, results, selectedArea)
                    }.setNegativeButton(com.bustime.app.R.string.cancel, null).show()
            }
        }
        content.addView(areaButton, LinearLayout.LayoutParams(-1, -2))
        content.addView(results)
        lifecycleScope.launch {
            val raw = withContext(Dispatchers.IO) { CommunitySupabase.getPublicOperators() }
            if (raw == null) {
                results.addView(TextView(this@BookBusActivity).apply { text = getString(com.bustime.app.R.string.bb_load_failed) })
                return@launch
            }
            val rows = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
            val allRows = (0 until rows.length()).map { rows.getJSONObject(it) }
            allRowsCache = allRows
            areaButton.text = "Permanent Area: All Areas ▾"
            renderOperators(allRows, results, selectedArea)
        }
    }

    private fun renderOperators(allRows: List<org.json.JSONObject>, results: LinearLayout, selectedArea: String?) {
            results.removeAllViews()
            val visibleRows = allRows.filter { item ->
                selectedArea == null || (item.optString("area_name").ifBlank { item.optString("location") }.trim().equals(selectedArea, ignoreCase = true))
            }
            if (visibleRows.isEmpty()) results.addView(TextView(this@BookBusActivity).apply { text = getString(com.bustime.app.R.string.bb_no_operators); textSize = 15f; setPadding(0, 8, 0, 8) })
            for (item in visibleRows) {
                val card = LinearLayout(this@BookBusActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(14, 14, 14, 14); setBackgroundColor(getColor(com.bustime.app.R.color.surfaceVariant)); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 } }
                val name = item.optString("operator_name", getString(com.bustime.app.R.string.bb_bus_operator))
                card.addView(TextView(this@BookBusActivity).apply { text = name; textSize = 19f; setTypeface(null, android.graphics.Typeface.BOLD) })
                val busType = localizedCategory(item.optString("bus_type", getString(com.bustime.app.R.string.bb_bus)))
                val variant = item.optString("custom_category", "")
                val seatType = localizedSeatType(item.optString("seat_type", "").ifBlank { getString(com.bustime.app.R.string.bb_not_specified) })
                val capacity = listOfNotNull(
                    if (!item.isNull("sleeper_count")) item.optInt("sleeper_count").takeIf { it >= 0 }?.let { getString(com.bustime.app.R.string.bb_sleeper_berths_format, it) } else null,
                    if (!item.isNull("seater_count")) item.optInt("seater_count").takeIf { it >= 0 }?.let { getString(com.bustime.app.R.string.bb_seats_format, it) } else null
                ).joinToString(" · ")
                card.addView(TextView(this@BookBusActivity).apply { text = "${item.optString("service_name", getString(com.bustime.app.R.string.bb_service))} · $busType" + (if (variant.isNotBlank()) " · $variant" else "") + " · $seatType" + (if (capacity.isNotBlank()) " · $capacity" else ""); textSize = 15f; setPadding(0, 6, 0, 3) })
                val detailLines = listOf(getString(com.bustime.app.R.string.bb_service_label, item.optString("service_name", "-")), getString(com.bustime.app.R.string.bb_bus_type_label, busType), getString(com.bustime.app.R.string.bb_bus_variant_label, variant), getString(com.bustime.app.R.string.bb_seat_type_label, seatType), item.optString("details", "")).filter { it.isNotBlank() }
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
}
