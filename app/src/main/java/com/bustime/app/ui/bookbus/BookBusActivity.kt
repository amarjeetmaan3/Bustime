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
        root.addView(TextView(this).apply { text = getString(com.bustime.app.R.string.bb_intro); textSize = 15f; setPadding(0, 8, 0, 16) })
        val scroll = ScrollView(this).apply { addView(root) }; setContentView(scroll)
        lifecycleScope.launch {
            val raw = withContext(Dispatchers.IO) { CommunitySupabase.getPublicOperators() }
            if (raw == null) { root.addView(TextView(this@BookBusActivity).apply { text = getString(com.bustime.app.R.string.bb_load_failed) }); return@launch }
            val rows = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
            if (rows.length() == 0) { root.addView(TextView(this@BookBusActivity).apply { text = getString(com.bustime.app.R.string.bb_none); textSize = 16f }); return@launch }
            for (i in 0 until rows.length()) {
                val item = rows.getJSONObject(i)
                val card = LinearLayout(this@BookBusActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(14, 14, 14, 14); setBackgroundColor(0xFFF3F6FA.toInt()); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 } }
                val name = item.optString("operator_name", "Bus Operator")
                card.addView(TextView(this@BookBusActivity).apply { text = name; textSize = 19f; setTypeface(null, android.graphics.Typeface.BOLD) })
                val routeSummary = "${item.optString("from_text", "-")} → ${item.optString("to_text", "-")}"
                val busType = item.optString("bus_type", "Bus")
                val seatType = item.optString("seat_type", "").ifBlank { getString(com.bustime.app.R.string.bb_seat_unspecified) }
                card.addView(TextView(this@BookBusActivity).apply { text = "${item.optString("service_name", getString(com.bustime.app.R.string.bb_service))} · $busType · $seatType"; textSize = 15f; setPadding(0, 6, 0, 3) })
                card.addView(TextView(this@BookBusActivity).apply { text = "${getString(com.bustime.app.R.string.bb_route)}: $routeSummary"; textSize = 14f; setPadding(0, 2, 0, 5) })
                val detailLines = listOf("Service: ${item.optString("service_name", "-")}", "Bus type: $busType", "Seat type: $seatType", "${getString(com.bustime.app.R.string.bb_route)}: $routeSummary", "${getString(com.bustime.app.R.string.bb_location)}: ${item.optString("location", "-" )}", item.optString("details", "")).filter { it.isNotBlank() }
                val detailBox = LinearLayout(this@BookBusActivity).apply { orientation = LinearLayout.VERTICAL; visibility = android.view.View.GONE }
                detailLines.forEach { line -> detailBox.addView(TextView(this@BookBusActivity).apply { text = line; textSize = 14f; setPadding(0, 5, 0, 0) }) }
                card.addView(TextView(this@BookBusActivity).apply { text = getString(com.bustime.app.R.string.bb_expand); textSize = 13f; setPadding(0, 8, 0, 4); setOnClickListener { detailBox.visibility = if (detailBox.visibility == android.view.View.VISIBLE) android.view.View.GONE else android.view.View.VISIBLE } })
                card.addView(detailBox)
                val phone = item.optString("phone", "").filter { it.isDigit() || it == '+' }
                if (phone.isNotBlank()) card.addView(TextView(this@BookBusActivity).apply {
                    text = "☎  ${getString(com.bustime.app.R.string.bb_contact)}: $phone"; textSize = 16f; setTextColor(0xFF0D47A1.toInt()); gravity = Gravity.CENTER_VERTICAL; setPadding(0, 12, 0, 2)
                    setOnClickListener { try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) } catch (_: Exception) { Toast.makeText(this@BookBusActivity, com.bustime.app.R.string.bb_call_error, Toast.LENGTH_SHORT).show() } }
                })
                val whatsapp = item.optString("whatsapp", phone).filter { it.isDigit() || it == '+' }
                if (whatsapp.isNotBlank()) card.addView(TextView(this@BookBusActivity).apply {
                    text = "${getString(com.bustime.app.R.string.bb_whatsapp)}: $whatsapp"; textSize = 16f; setTextColor(0xFF128C7E.toInt()); setPadding(0, 8, 0, 2)
                    setOnClickListener { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${whatsapp.filter { c -> c.isDigit() }}"))) } catch (_: Exception) { Toast.makeText(this@BookBusActivity, com.bustime.app.R.string.bb_whatsapp_error, Toast.LENGTH_SHORT).show() } }
                })
                root.addView(card)
            }
        }
    }
}
