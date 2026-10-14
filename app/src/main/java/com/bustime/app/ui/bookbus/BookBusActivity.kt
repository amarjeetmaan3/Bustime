package com.bustime.app.ui.bookbus

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bustime.app.R
import com.bustime.app.ui.community.CommunitySupabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Public list of approved bus services. Contact details are shown only on the detail screen. */
class BookBusActivity : AppCompatActivity() {
    private fun localizedCategory(value: String): String = when (value.trim().lowercase(java.util.Locale.ROOT)) {
        "ordinary" -> getString(R.string.bus_category_ordinary)
        "luxury" -> getString(R.string.bus_category_luxury)
        "sleeper" -> getString(R.string.bus_category_sleeper)
        "sleeper plus" -> getString(R.string.bus_category_sleeper_plus)
        "seater plus" -> getString(R.string.bus_category_seater_plus)
        "seater" -> getString(R.string.bus_category_seater)
        "sleeper + seater" -> getString(R.string.bus_category_sleeper_seater)
        "custom" -> getString(R.string.bus_category_custom)
        "ac bus" -> getString(R.string.bus_type_ac)
        "non-ac bus" -> getString(R.string.bus_type_non_ac)
        "volvo bus" -> getString(R.string.bus_type_volvo)
        "mini bus" -> getString(R.string.bus_type_mini)
        "other" -> getString(R.string.bus_type_other)
        else -> value
    }

    private fun localizedSeatType(value: String): String = when (value.trim().lowercase(java.util.Locale.ROOT)) {
        "sleeper + seater" -> getString(R.string.bus_category_sleeper_seater)
        "sleeper" -> getString(R.string.bus_category_sleeper)
        "seater" -> getString(R.string.bus_category_seater)
        "not specified" -> getString(R.string.bb_not_specified)
        else -> value
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.book_bus)

        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val pageBg = if (night) Color.rgb(18, 22, 29) else Color.rgb(245, 247, 251)
        val cardBg = if (night) Color.rgb(29, 35, 44) else Color.WHITE
        val primaryText = if (night) Color.rgb(238, 242, 247) else Color.rgb(24, 34, 48)
        val secondaryText = if (night) Color.rgb(174, 185, 200) else Color.rgb(102, 117, 138)
        val borderColor = if (night) Color.rgb(57, 66, 80) else Color.rgb(229, 234, 242)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(pageBg)
        }
        val scroll = ScrollView(this).apply { clipToPadding = false }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        content.addView(TextView(this).apply {
            text = getString(R.string.bb_intro)
            textSize = 15f
            setTextColor(secondaryText)
            setPadding(dp(2), dp(4), dp(2), dp(14))
        })

        var selectedArea: String? = null
        var searchQuery = ""
        var allRowsCache: List<JSONObject> = emptyList()
        val searchBox = EditText(this).apply {
            hint = getString(R.string.bb_search_hint)
            singleLine = true
            textSize = 15f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                setColor(cardBg)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), borderColor)
            }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchQuery = s?.toString()?.trim().orEmpty()
                    renderOperators(allRowsCache, results, selectedArea, searchQuery, cardBg, primaryText, secondaryText, borderColor)
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        content.addView(searchBox, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val loading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(18), dp(4), dp(18))
        }
        loading.addView(ProgressBar(this), LinearLayout.LayoutParams(dp(28), dp(28)))
        loading.addView(TextView(this).apply {
            text = getString(R.string.bb_loading)
            textSize = 14f
            setTextColor(secondaryText)
            setPadding(dp(12), 0, 0, 0)
        })
        val areaButton = Button(this).apply {
            text = getString(R.string.area_change_label, getString(R.string.area_all_areas))
            isEnabled = false
            setOnClickListener {
                val areas = allRowsCache.mapNotNull { it.optString("location").trim().takeIf(String::isNotBlank) }
                    .distinctBy { it.lowercase(java.util.Locale.ROOT) }
                val choices = listOf(getString(R.string.area_all_areas)) + areas
                AlertDialog.Builder(this@BookBusActivity)
                    .setTitle(R.string.area_change_title)
                    .setSingleChoiceItems(choices.toTypedArray(), selectedArea?.let { area -> areas.indexOfFirst { it.equals(area, true) } + 1 } ?: 0) { dialog, which ->
                        selectedArea = if (which == 0) null else areas[which - 1]
                        text = getString(R.string.area_change_label, selectedArea ?: getString(R.string.area_all_areas))
                        dialog.dismiss()
                        renderOperators(allRowsCache, results, selectedArea, searchQuery, cardBg, primaryText, secondaryText, borderColor)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
        content.addView(areaButton, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        content.addView(loading)
        content.addView(results)

        val addBus = com.google.android.material.button.MaterialButton(this).apply {
            text = getString(R.string.bb_add_your_bus)
            setOnClickListener {
                startActivity(Intent(this@BookBusActivity, com.bustime.app.ui.community.CommunityRequestActivity::class.java).putExtra("mode", "bus_operator"))
            }
        }
        root.addView(addBus, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        setContentView(root)

        lifecycleScope.launch {
            val raw = withContext(Dispatchers.IO) { CommunitySupabase.getPublicOperators() }
            loading.visibility = View.GONE
            if (raw == null) {
                results.addView(TextView(this@BookBusActivity).apply {
                    text = getString(R.string.bb_load_failed)
                    textSize = 15f
                    setTextColor(primaryText)
                    setPadding(dp(4), dp(12), dp(4), dp(12))
                })
                return@launch
            }
            val rows = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
            allRowsCache = (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
            areaButton.isEnabled = allRowsCache.any { it.optString("location").isNotBlank() }
            areaButton.text = getString(R.string.area_change_label, getString(R.string.area_all_areas))
            renderOperators(allRowsCache, results, selectedArea, searchQuery, cardBg, primaryText, secondaryText, borderColor)
        }
    }

    private fun renderOperators(
        allRows: List<JSONObject>,
        results: LinearLayout,
        selectedArea: String?,
        searchQuery: String,
        cardBg: Int,
        primaryText: Int,
        secondaryText: Int,
        borderColor: Int
    ) {
        results.removeAllViews()
        val visibleRows = allRows.filter { item ->
            val areaMatches = selectedArea == null || item.optString("location").trim().equals(selectedArea, ignoreCase = true)
            val types = try {
                val arr = item.optJSONArray("bus_types")
                if (arr != null) (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
                else listOf(item.optString("bus_type")).filter(String::isNotBlank)
            } catch (_: Exception) { listOf(item.optString("bus_type")).filter(String::isNotBlank) }
            val searchable = listOf(item.optString("service_name"), item.optString("operator_name"), item.optString("location"), item.optString("details")) .plus(types).plus(types.map(::localizedCategory)).joinToString(" ")
            areaMatches && (searchQuery.isBlank() || searchable.contains(searchQuery, ignoreCase = true))
        }
        if (visibleRows.isEmpty()) {
            results.addView(TextView(this).apply {
                text = if (searchQuery.isNotBlank()) getString(R.string.bb_search_no_results, searchQuery) else if (selectedArea == null) getString(R.string.bb_no_operators) else getString(R.string.bb_no_options_area, selectedArea)
                textSize = 15f
                setTextColor(secondaryText)
                setPadding(dp(4), dp(12), dp(4), dp(12))
            })
            return
        }
        visibleRows.forEach { item ->
            val serviceName = item.optString("service_name").trim().ifBlank { item.optString("operator_name").trim() }.ifBlank { getString(R.string.bb_service) }
            val operatorName = item.optString("operator_name").trim()
            val types = try {
                val arr = item.optJSONArray("bus_types")
                if (arr != null) (0 until arr.length()).mapNotNull { arr.optString(it).trim().takeIf(String::isNotBlank) }
                else listOf(item.optString("bus_type").trim()).filter(String::isNotBlank)
            } catch (_: Exception) { listOf(item.optString("bus_type").trim()).filter(String::isNotBlank) }
            val typeLine = types.joinToString(" · ") { localizedCategory(it) }
            val location = item.optString("location").trim()
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(15), dp(16), dp(15))
                background = GradientDrawable().apply {
                    setColor(cardBg); cornerRadius = dp(16).toFloat(); setStroke(dp(1), borderColor)
                }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
            }
            card.addView(TextView(this).apply {
                text = serviceName; textSize = 19f; typeface = Typeface.DEFAULT_BOLD; setTextColor(primaryText)
            })
            if (typeLine.isNotBlank()) card.addView(TextView(this).apply {
                text = typeLine; textSize = 14f; setTextColor(secondaryText); setPadding(0, dp(5), 0, 0)
            })
            if (location.isNotBlank()) card.addView(TextView(this).apply {
                text = "${getString(R.string.bb_location)}: $location"; textSize = 13f; setTextColor(secondaryText); setPadding(0, dp(4), 0, 0)
            })
            card.addView(com.google.android.material.button.MaterialButton(this).apply {
                text = getString(R.string.bb_details_button)
                setOnClickListener {
                    startActivity(Intent(this@BookBusActivity, BusOperatorDetailActivity::class.java).apply {
                        putExtra("operator_name", operatorName)
                        putExtra("service_name", serviceName)
                        putExtra("bus_types", org.json.JSONArray(types).toString())
                        putExtra("location", location)
                        putExtra("details", item.optString("details"))
                        putExtra("phone", item.optString("phone"))
                        putExtra("whatsapp", item.optString("whatsapp"))
                    })
                }
            }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(12) })
            results.addView(card)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
