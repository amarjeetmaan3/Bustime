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
        title = "Book Bus · Contact Operator"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 12, 18, 18) }
        root.addView(TextView(this).apply { text = "Choose a bus service and contact the operator directly. Booking and payment are handled outside the app."; textSize = 15f; setPadding(0, 8, 0, 16) })
        val scroll = ScrollView(this).apply { addView(root) }; setContentView(scroll)
        lifecycleScope.launch {
            val raw = withContext(Dispatchers.IO) { CommunitySupabase.getPublicOperators() }
            if (raw == null) { root.addView(TextView(this@BookBusActivity).apply { text = "Could not load bus services. Check your internet connection." }); return@launch }
            val rows = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
            if (rows.length() == 0) { root.addView(TextView(this@BookBusActivity).apply { text = "No bus services have been published yet."; textSize = 16f }); return@launch }
            for (i in 0 until rows.length()) {
                val item = rows.getJSONObject(i)
                val card = LinearLayout(this@BookBusActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(14, 14, 14, 14); setBackgroundColor(0xFFF3F6FA.toInt()); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 } }
                val name = item.optString("operator_name", "Bus Operator")
                card.addView(TextView(this@BookBusActivity).apply { text = name; textSize = 19f; setTypeface(null, android.graphics.Typeface.BOLD) })
                listOf("Service: ${item.optString("service_name", "-")}", "Bus type: ${item.optString("bus_type", "-")}", "Route: ${item.optString("from_text", "-")} → ${item.optString("to_text", "-")}", item.optString("details", "")).filter { it.isNotBlank() }.forEach { line ->
                    card.addView(TextView(this@BookBusActivity).apply { text = line; textSize = 14f; setPadding(0, 5, 0, 0) })
                }
                val phone = item.optString("phone", "").filter { it.isDigit() || it == '+' }
                if (phone.isNotBlank()) card.addView(TextView(this@BookBusActivity).apply {
                    text = "☎  Contact operator: $phone"; textSize = 16f; setTextColor(0xFF0D47A1.toInt()); gravity = Gravity.CENTER_VERTICAL; setPadding(0, 12, 0, 2)
                    setOnClickListener { try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) } catch (_: Exception) { Toast.makeText(this@BookBusActivity, "Could not open phone dialer", Toast.LENGTH_SHORT).show() } }
                })
                root.addView(card)
            }
        }
    }
}
