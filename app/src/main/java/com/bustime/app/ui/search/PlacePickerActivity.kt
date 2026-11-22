package com.bustime.app.ui.search

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.bustime.app.R
import com.bustime.app.data.repository.AppRepository
import com.bustime.app.models.Place
import com.bustime.app.ui.common.applyBouncyPress
import com.bustime.app.utils.LanguageHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * जगह चुनने का पूरा पेज: ऊपर लिखने की जगह, नीचे सुझावों की लिस्ट। कीबोर्ड खुलने पर लिस्ट उसके ऊपर रहती है।
 * Extras: STANDS_ONLY (सिर्फ़ बस स्टैंड), HINT। नतीजा: PLACE_ID
 */
class PlacePickerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_place_picker)

        val standsOnly = intent.getBooleanExtra("STANDS_ONLY", false)
        val et = findViewById<EditText>(R.id.etPickerSearch)
        et.hint = intent.getStringExtra("HINT") ?: getString(R.string.type_place_hint)
        val list = findViewById<LinearLayout>(R.id.pickerList)
        val tvEmpty = findViewById<TextView>(R.id.tvPickerEmpty)
        findViewById<Button>(R.id.btnBackPicker).visibility = View.GONE
        findViewById<ImageButton>(R.id.btnNavBackPicker)?.setOnClickListener { finish() }

        val repo = AppRepository.getInstance(this)
        val lang = LanguageHelper.current()
        val density = resources.displayMetrics.density
        val bodyFont = ResourcesCompat.getFont(this, R.font.plus_jakarta_sans)
        val surfaceColor = ContextCompat.getColor(this, R.color.surface)
        val dividerColor = ContextCompat.getColor(this, R.color.divider)
        val rippleColor = ContextCompat.getColor(this, R.color.colorPrimaryContainer)
        val primaryColor = ContextCompat.getColor(this, R.color.colorPrimary)
        val peachBg = ContextCompat.getColor(this, R.color.colorSecondaryContainer)
        val saffronColor = ContextCompat.getColor(this, R.color.colorAccent)
        val textPrimary = ContextCompat.getColor(this, R.color.textPrimary)
        val textSecondary = ContextCompat.getColor(this, R.color.textSecondary)
        var job: Job? = null

        fun dp(value: Int): Int = (value * density).toInt()

        fun typedLanguage(query: String): String {
            val pa = query.count { it.code in 0x0A00..0x0A7F }
            val hi = query.count { it.code in 0x0900..0x097F }
            val en = query.count { it in 'a'..'z' || it in 'A'..'Z' }
            return when {
                pa > hi && pa > en -> "pa"
                hi > pa && hi > en -> "hi"
                en > 0 -> "en"
                hi > 0 -> "hi"
                pa > 0 -> "pa"
                else -> lang
            }
        }

        fun render(places: List<Place>, query: String) {
            list.removeAllViews()
            tvEmpty.visibility = if (places.isEmpty()) View.VISIBLE else View.GONE
            val displayLang = typedLanguage(query)
            for (p in places) {
                val primaryName = p.displayName(displayLang)
                val altNames = p.names.entries
                    .filter { (code, value) -> code != displayLang && value.isNotBlank() && !value.equals(primaryName, true) }
                    .map { it.value.trim() }
                    .distinct()
                    .joinToString("  •  ")
                val district = p.district?.trim()?.takeIf { it.isNotBlank() }

                val shape = GradientDrawable().apply {
                    setColor(surfaceColor)
                    cornerRadius = dp(16).toFloat()
                    setStroke(dp(1), dividerColor)
                }
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(62)
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    background = RippleDrawable(ColorStateList.valueOf(rippleColor), shape, null)
                    elevation = dp(1).toFloat()
                    isClickable = true
                    isFocusable = true
                    applyBouncyPress()
                    setOnClickListener {
                        setResult(RESULT_OK, Intent().putExtra("PLACE_ID", p.id))
                        finish()
                    }
                }

                // Circular Location Icon Badge
                val pinBadge = FrameLayout(this).apply {
                    background = ContextCompat.getDrawable(this@PlacePickerActivity, R.drawable.bg_icon_badge)
                }
                pinBadge.addView(ImageView(this).apply {
                    setImageResource(R.drawable.ic_place_pin)
                    imageTintList = ColorStateList.valueOf(primaryColor)
                    contentDescription = primaryName
                }, FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
                row.addView(pinBadge, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) })

                // Primary Name + Multilingual Subtitle
                val textCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                textCol.addView(TextView(this).apply {
                    text = primaryName
                    textSize = 16f
                    typeface = Typeface.create(bodyFont, Typeface.BOLD)
                    setTextColor(textPrimary)
                })
                if (altNames.isNotBlank()) {
                    textCol.addView(TextView(this).apply {
                        text = altNames
                        textSize = 12.5f
                        typeface = bodyFont
                        setTextColor(textSecondary)
                        setPadding(0, dp(2), 0, 0)
                    })
                }
                row.addView(textCol, LinearLayout.LayoutParams(0, -2, 1f))

                // Optional District Chip
                if (district != null) {
                    val chip = TextView(this).apply {
                        text = district
                        textSize = 11.5f
                        typeface = Typeface.create(bodyFont, Typeface.BOLD)
                        setTextColor(saffronColor)
                        setPadding(dp(9), dp(4), dp(9), dp(4))
                        background = GradientDrawable().apply {
                            setColor(peachBg)
                            cornerRadius = dp(10).toFloat()
                        }
                    }
                    row.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
                }

                list.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(4)
                    bottomMargin = dp(4)
                })
            }
        }

        fun load(query: String, delayMs: Long) {
            job?.cancel()
            job = lifecycleScope.launch {
                if (delayMs > 0) delay(delayMs)
                render(repo.searchPlaces(query, standsOnly, 40, allowEmpty = true), query)
            }
        }

        et.addTextChangedListener { text -> load(text?.toString().orEmpty(), 120) }
        load("", 0)   // खुलते ही सूची दिखे
        et.requestFocus()
    }
}
