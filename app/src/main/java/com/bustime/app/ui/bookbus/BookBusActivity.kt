package com.bustime.app.ui.bookbus

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.bustime.app.R
import com.bustime.app.ads.StartIoAdsManager
import com.bustime.app.ui.common.applyBouncyPress
import com.bustime.app.ui.community.CommunitySupabase
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Public list of approved bus services. Contact details are shown only on the detail screen. */
class BookBusActivity : AppCompatActivity() {
    private val bodyFont: Typeface? by lazy { ResourcesCompat.getFont(this, R.font.plus_jakarta_sans) }

    private fun localizedCategory(value: String): String = when (value.trim().lowercase(java.util.Locale.ROOT)) {
        "ordinary" -> getString(R.string.bus_category_ordinary)
        "luxury", "luxury bus" -> getString(R.string.bus_type_luxury)
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

        val pageBg = ContextCompat.getColor(this, R.color.bgScreen)
        val cardBg = ContextCompat.getColor(this, R.color.surface)
        val primaryText = ContextCompat.getColor(this, R.color.textPrimary)
        val secondaryText = ContextCompat.getColor(this, R.color.textSecondary)
        val borderColor = ContextCompat.getColor(this, R.color.divider)
        val primaryColor = ContextCompat.getColor(this, R.color.colorPrimary)
        val heroEnd = ContextCompat.getColor(this, R.color.heroEnd)
        val accentColor = ContextCompat.getColor(this, R.color.colorAccent)
        val tonalBg = ContextCompat.getColor(this, R.color.colorPrimaryContainer)
        val peachBg = ContextCompat.getColor(this, R.color.colorSecondaryContainer)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBg)
        }

        // Royal Indigo Gradient Header Bar
        val headerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(this@BookBusActivity, R.drawable.bg_hero_gradient)
            elevation = dp(4).toFloat()
            setPadding(dp(10), dp(10), dp(14), dp(10))
        }
        headerBar.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_back_arrow)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            background = null
            contentDescription = getString(R.string.back)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        headerBar.addView(TextView(this).apply {
            text = getString(R.string.book_bus)
            textSize = 20f
            typeface = Typeface.create(bodyFont, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(6), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(headerBar, LinearLayout.LayoutParams(-1, -2))

        val scroll = ScrollView(this).apply { clipToPadding = false }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(18))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        content.addView(TextView(this).apply {
            text = getString(R.string.bb_intro)
            textSize = 14f
            typeface = bodyFont
            setLineSpacing(dp(3).toFloat(), 1f)
            setTextColor(secondaryText)
            setPadding(dp(2), dp(2), dp(2), dp(12))
        })

        // Pill Search Bar for quick filtering
        var searchQuery = ""
        var selectedArea: String? = null
        var allRowsCache: List<JSONObject> = emptyList()
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val searchPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            background = GradientDrawable().apply {
                setColor(cardBg)
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), borderColor)
            }
            elevation = dp(2).toFloat()
        }
        searchPill.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_search_modern)
            imageTintList = ColorStateList.valueOf(primaryColor)
            contentDescription = getString(R.string.search)
        }, LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(10) })
        val searchInput = EditText(this).apply {
            hint = getString(R.string.bb_search_hint)
            textSize = 14.5f
            typeface = bodyFont
            setTextColor(primaryText)
            setHintTextColor(secondaryText)
            background = null
            maxLines = 1
            addTextChangedListener { editable ->
                searchQuery = editable?.toString().orEmpty().trim()
                renderOperators(allRowsCache, results, selectedArea, searchQuery, cardBg, primaryText, secondaryText, borderColor, primaryColor, heroEnd, tonalBg, peachBg, accentColor)
            }
        }
        searchPill.addView(searchInput, LinearLayout.LayoutParams(0, dp(48), 1f))
        content.addView(searchPill, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(10) })

        val loading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(18), dp(6), dp(18))
        }
        loading.addView(ProgressBar(this), LinearLayout.LayoutParams(dp(26), dp(26)))
        loading.addView(TextView(this).apply {
            text = getString(R.string.bb_loading)
            textSize = 14f
            typeface = bodyFont
            setTextColor(secondaryText)
            setPadding(dp(12), 0, 0, 0)
        })

        val areaButton = MaterialButton(this).apply {
            text = getString(R.string.area_change_label, getString(R.string.area_all_areas))
            isAllCaps = false
            isEnabled = false
            typeface = Typeface.create(bodyFont, Typeface.BOLD)
            cornerRadius = dp(24)
            backgroundTintList = ColorStateList.valueOf(tonalBg)
            setTextColor(primaryColor)
            strokeColor = ColorStateList.valueOf(borderColor)
            strokeWidth = dp(1)
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
                        renderOperators(allRowsCache, results, selectedArea, searchQuery, cardBg, primaryText, secondaryText, borderColor, primaryColor, heroEnd, tonalBg, peachBg, accentColor)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
        content.addView(areaButton, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(14) })
        content.addView(loading)
        content.addView(results)

        // Sticky Saffron Gradient CTA Footer
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cardBg)
            elevation = dp(8).toFloat()
        }
        footer.addView(View(this).apply { setBackgroundColor(borderColor) }, LinearLayout.LayoutParams(-1, dp(1)))
        val addBus = TextView(this).apply {
            text = getString(R.string.bb_add_your_bus)
            gravity = Gravity.CENTER
            textSize = 15.5f
            typeface = Typeface.create(bodyFont, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = ContextCompat.getDrawable(this@BookBusActivity, R.drawable.bg_saffron_cta)
            elevation = dp(2).toFloat()
            isClickable = true
            isFocusable = true
            applyBouncyPress()
            setOnClickListener {
                startActivity(Intent(this@BookBusActivity, com.bustime.app.ui.community.CommunityRequestActivity::class.java).putExtra("mode", "bus_operator"))
            }
        }
        footer.addView(addBus, LinearLayout.LayoutParams(-1, dp(52)).apply {
            setMargins(dp(16), dp(12), dp(16), dp(12))
        })
        root.addView(footer, LinearLayout.LayoutParams(-1, -2))

        val startIoBannerSlot = FrameLayout(this).apply { visibility = View.GONE }
        root.addView(startIoBannerSlot, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        StartIoAdsManager.attachBanner(this, startIoBannerSlot, "book_bus")

        lifecycleScope.launch {
            val raw = withContext(Dispatchers.IO) { CommunitySupabase.getPublicOperators() }
            loading.visibility = View.GONE
            if (raw == null) {
                results.addView(TextView(this@BookBusActivity).apply {
                    text = getString(R.string.bb_load_failed)
                    textSize = 15f
                    typeface = bodyFont
                    setTextColor(primaryText)
                    setPadding(dp(4), dp(12), dp(4), dp(12))
                })
                return@launch
            }
            val rows = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
            allRowsCache = (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
            areaButton.isEnabled = allRowsCache.any { it.optString("location").isNotBlank() }
            areaButton.text = getString(R.string.area_change_label, getString(R.string.area_all_areas))
            renderOperators(allRowsCache, results, selectedArea, searchQuery, cardBg, primaryText, secondaryText, borderColor, primaryColor, heroEnd, tonalBg, peachBg, accentColor)
        }
    }

    private fun renderOperators(
        allRows: List<JSONObject>,
        results: LinearLayout,
        selectedArea: String?,
        query: String,
        cardBg: Int,
        primaryText: Int,
        secondaryText: Int,
        borderColor: Int,
        primaryColor: Int,
        heroEnd: Int,
        tonalBg: Int,
        peachBg: Int,
        accentColor: Int
    ) {
        results.removeAllViews()
        val visibleRows = allRows.filter { item ->
            val areaMatches = selectedArea == null || item.optString("location").trim().equals(selectedArea, ignoreCase = true)
            val qMatches = query.isBlank() ||
                item.optString("service_name").contains(query, true) ||
                item.optString("operator_name").contains(query, true) ||
                item.optString("location").contains(query, true) ||
                item.optString("bus_type").contains(query, true) ||
                item.optString("bus_types").contains(query, true)
            areaMatches && qMatches
        }
        if (visibleRows.isEmpty()) {
            results.addView(TextView(this).apply {
                text = when {
                    query.isNotBlank() -> getString(R.string.bb_search_no_results, query)
                    selectedArea == null -> getString(R.string.bb_no_operators)
                    else -> getString(R.string.bb_no_options_area, selectedArea)
                }
                textSize = 15f
                typeface = bodyFont
                setTextColor(secondaryText)
                setPadding(dp(4), dp(14), dp(4), dp(14))
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

            val initials = serviceName.split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.first().uppercaseChar().toString() }
                .ifBlank { "BT" }

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(16), dp(18), dp(16))
                elevation = dp(3).toFloat()
                background = GradientDrawable().apply {
                    setColor(cardBg); cornerRadius = dp(22).toFloat(); setStroke(dp(1), borderColor)
                }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
                applyBouncyPress()
            }

            // Header row with Gradient Circular Monogram Avatar + Name + Location
            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val monogram = TextView(this).apply {
                text = initials
                textSize = 17f
                typeface = Typeface.create(bodyFont, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(primaryColor, heroEnd)
                ).apply { shape = GradientDrawable.OVAL }
            }
            topRow.addView(monogram, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(14) })

            val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            titleCol.addView(TextView(this).apply {
                text = serviceName
                textSize = 17.5f
                typeface = Typeface.create(bodyFont, Typeface.BOLD)
                setTextColor(primaryText)
            })
            if (location.isNotBlank()) titleCol.addView(TextView(this).apply {
                text = "${getString(R.string.bb_location)}: $location"
                textSize = 13f
                typeface = bodyFont
                setTextColor(secondaryText)
                setPadding(0, dp(3), 0, 0)
            })
            topRow.addView(titleCol, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(topRow)

            if (typeLine.isNotBlank()) card.addView(TextView(this).apply {
                text = typeLine
                textSize = 12.5f
                typeface = Typeface.create(bodyFont, Typeface.BOLD)
                setTextColor(accentColor)
                setPadding(dp(10), dp(4), dp(10), dp(4))
                background = GradientDrawable().apply {
                    setColor(peachBg)
                    cornerRadius = dp(10).toFloat()
                }
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10) })

            card.addView(MaterialButton(this).apply {
                text = getString(R.string.bb_details_button)
                isAllCaps = false
                typeface = Typeface.create(bodyFont, Typeface.BOLD)
                cornerRadius = dp(24)
                backgroundTintList = ColorStateList.valueOf(primaryColor)
                setTextColor(Color.WHITE)
                setOnClickListener {
                    startActivity(Intent(this@BookBusActivity, BusOperatorDetailActivity::class.java).apply {
                        putExtra("operator_name", operatorName)
                        putExtra("service_name", serviceName)
                        putExtra("bus_types", JSONArray(types).toString())
                        putExtra("location", location)
                        putExtra("details", item.optString("details"))
                        putExtra("phone", item.optString("phone"))
                        putExtra("whatsapp", item.optString("whatsapp"))
                    })
                }
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(14) })
            results.addView(card)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
