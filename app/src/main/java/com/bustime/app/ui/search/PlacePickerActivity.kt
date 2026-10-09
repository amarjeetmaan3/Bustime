package com.bustime.app.ui.search

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.bustime.app.R
import com.bustime.app.data.repository.AppRepository
import com.bustime.app.models.Place
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
        findViewById<Button>(R.id.btnBackPicker).setOnClickListener { finish() }

        val repo = AppRepository.getInstance(this)
        val lang = LanguageHelper.current()
        val density = resources.displayMetrics.density
        var job: Job? = null

        fun render(places: List<Place>) {
            list.removeAllViews()
            tvEmpty.visibility = if (places.isEmpty()) View.VISIBLE else View.GONE
            for (p in places) {
                val tv = TextView(this).apply {
                    text = p.displayName(lang)
                    textSize = 18f
                    setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
                    setBackgroundResource(android.R.drawable.list_selector_background)
                    setOnClickListener {
                        setResult(RESULT_OK, Intent().putExtra("PLACE_ID", p.id))
                        finish()
                    }
                }
                list.addView(tv)
            }
        }

        fun load(query: String, delayMs: Long) {
            job?.cancel()
            job = lifecycleScope.launch {
                if (delayMs > 0) delay(delayMs)
                render(repo.searchPlaces(query, standsOnly, 40, allowEmpty = true))
            }
        }

        et.addTextChangedListener { text -> load(text?.toString().orEmpty(), 120) }
        load("", 0)   // खुलते ही सूची दिखे
        et.requestFocus()
    }
}
