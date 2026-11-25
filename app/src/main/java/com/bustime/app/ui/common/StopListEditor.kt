package com.bustime.app.ui.common

import android.app.TimePickerDialog
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.bustime.app.R
import com.bustime.app.models.Place
import com.bustime.app.utils.LanguageHelper
import com.google.android.material.button.MaterialButton
import org.json.JSONObject
import java.util.Locale

/**
 * रूट के पड़ावों की एक जैसी लिस्ट: Route Correction और Add Route दोनों स्क्रीन यही इस्तेमाल करती हैं,
 * इसलिए दोनों हमेशा एक जैसी दिखती और चलती हैं।
 *
 * हर लाइन: नंबर (पहला Emerald, आख़िरी Saffron, बीच के Indigo) | नाम (सुझावों के साथ) | समय (JetBrains Mono) | + (नीचे नया पड़ाव) | ⋮ (ऊपर / नीचे / हटाओ)।
 * ऊपर "Add Start" और नीचे "Add stop at end" बटन। जो जगह लिस्ट में पहले से है वह सुझाव में नहीं आती।
 *
 * drafts की हर एंट्री: {"place_id": id या null, "name": "...", "language": "en|hi|pa", "time": "HH:mm" या null}
 *
 * @param minStops इससे कम पड़ाव बचें तो हटाने नहीं देता (0 = कोई रोक नहीं)
 * @param removeWarning हटाते समय पूछने वाले संदेश का दूसरा हिस्सा (null हो तो सिर्फ़ सवाल)
 * @param validateTimes नया समय चुनने पर पूरे क्रम की जाँच; false लौटाए तो समय नहीं बदलता
 */
class StopListEditor(
    private val activity: AppCompatActivity,
    private val box: LinearLayout,
    private val allPlaces: List<Place>,
    val drafts: MutableList<JSONObject>,
    private val minStops: Int = 0,
    private val removeWarning: String? = null,
    private val validateTimes: ((List<String?>) -> Boolean)? = null
) {
    /** Same custom analog clock glyph used by the new External Admin app. */
    private class ClockGlyphView(context: android.content.Context) : android.view.View(context) {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = minOf(width, height) * 0.43f
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = 0xffeaf2fb.toInt()
            canvas.drawCircle(cx, cy, radius, paint)

            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = resources.displayMetrics.density * 1.8f
            paint.color = 0xff245b8f.toInt()
            canvas.drawCircle(cx, cy, radius, paint)

            for (i in 0 until 12) {
                val angle = Math.PI * i / 6.0
                val outer = radius * 0.78f
                val inner = radius * (if (i % 3 == 0) 0.57f else 0.68f)
                paint.strokeWidth = resources.displayMetrics.density * (if (i % 3 == 0) 1.8f else 1.1f)
                canvas.drawLine(
                    cx + Math.sin(angle).toFloat() * inner,
                    cy - Math.cos(angle).toFloat() * inner,
                    cx + Math.sin(angle).toFloat() * outer,
                    cy - Math.cos(angle).toFloat() * outer,
                    paint
                )
            }

            paint.strokeCap = android.graphics.Paint.Cap.ROUND
            paint.strokeWidth = resources.displayMetrics.density * 2.5f
            canvas.drawLine(cx, cy, cx, cy - radius * 0.48f, paint)
            canvas.drawLine(cx, cy, cx + radius * 0.38f, cy + radius * 0.20f, paint)
            paint.style = android.graphics.Paint.Style.FILL
            canvas.drawCircle(cx, cy, resources.displayMetrics.density * 2.2f, paint)
        }
    }

    private fun dp(n: Int): Int = (n * activity.resources.displayMetrics.density).toInt()

    fun newDraft(): JSONObject = JSONObject().put("place_id", JSONObject.NULL).put("name", "")
        .put("language", LanguageHelper.current()).put("time", JSONObject.NULL)

    private fun timeOf(d: JSONObject): String? =
        if (d.isNull("time")) null else d.optString("time").trim().takeIf { it.isNotBlank() && it != "null" }

    private fun timesWith(index: Int, value: String): List<String?> =
        drafts.mapIndexed { i, d -> if (i == index) value else timeOf(d) }

    /** Returns the first repeated stop positions (1-based) after resolving typed aliases to canonical places. */
    fun duplicateStopIndexes(): Pair<Int, Int>? {
        fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        val firstSeen = mutableMapOf<String, Int>()
        drafts.forEachIndexed { index, draft ->
            val name = draft.optString("name").trim()
            if (name.isBlank()) return@forEachIndexed
            val explicitId = if (draft.isNull("place_id")) "" else draft.optString("place_id").trim()
            val canonical = if (explicitId.isNotBlank()) {
                allPlaces.firstOrNull { it.id == explicitId }
            } else {
                allPlaces.firstOrNull { place -> place.names.values.any { alias -> normalize(alias) == normalize(name) } }
            }
            val key = canonical?.let { "id:${it.id}" } ?: "name:${normalize(name)}"
            val previous = firstSeen[key]
            // A circular route may start and end at the same place; no other repeated stop is allowed.
            val allowedCircularEndpoint = previous == 1 && index + 1 == drafts.size
            if (previous != null && !allowedCircularEndpoint) return previous to (index + 1)
            if (previous == null) firstSeen[key] = index + 1
        }
        return null
    }

    fun render() {
        box.removeAllViews()
        val fontUi = ResourcesCompat.getFont(activity, R.font.plus_jakarta_sans)
        val fontMono = ResourcesCompat.getFont(activity, R.font.jetbrains_mono)
        val textPrimary = activity.getColor(R.color.textPrimary)
        val textSecondary = activity.getColor(R.color.textSecondary)
        val textMuted = activity.getColor(R.color.textMuted)
        val primaryColor = activity.getColor(R.color.colorPrimary)
        val primaryContainer = activity.getColor(R.color.colorPrimaryContainer)
        val accentColor = activity.getColor(R.color.colorAccent)
        val tertiaryColor = activity.getColor(R.color.colorTertiary)
        val fieldBg = activity.getColor(R.color.stopFieldBg)
        val dividerColor = activity.getColor(R.color.divider)
        val surfaceColor = activity.getColor(R.color.surface)
        val timeChipBg = activity.getColor(R.color.timeChipBg)
        val timeChipStroke = activity.getColor(R.color.timeChipStroke)
        val timeChipText = activity.getColor(R.color.timeChipText)

        fun buildActionButton(label: String, onClick: () -> Unit): MaterialButton =
            MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = label
                isAllCaps = false
                textSize = 13.5f
                typeface = Typeface.create(fontUi, Typeface.BOLD)
                cornerRadius = dp(14)
                setTextColor(primaryColor)
                backgroundTintList = ColorStateList.valueOf(primaryContainer)
                strokeColor = ColorStateList.valueOf(timeChipStroke)
                strokeWidth = dp(1)
                insetTop = 0
                insetBottom = 0
                applyBouncyPress()
                setOnClickListener { onClick() }
            }

        val addStart = buildActionButton(activity.getString(R.string.community_add_start)) {
            drafts.add(0, newDraft())
            render()
        }
        box.addView(addStart, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(10) })

        drafts.forEachIndexed { index, draft ->
            val isFirst = index == 0
            val isLast = index == drafts.lastIndex

            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(6), dp(6), dp(6))
                minimumHeight = dp(58)
                background = GradientDrawable().apply {
                    setColor(fieldBg)
                    setStroke(dp(1), dividerColor)
                    cornerRadius = dp(16).toFloat()
                }
            }

            // Station order pill: Emerald for Start, Saffron for Destination, Soft Indigo for Middle stops
            val badgeBgColor = when {
                isFirst -> tertiaryColor
                isLast -> accentColor
                else -> primaryContainer
            }
            val badgeTextColor = if (isFirst || isLast) Color.WHITE else primaryColor
            val number = TextView(activity).apply {
                text = (index + 1).toString()
                textSize = 12.5f
                typeface = Typeface.create(fontMono, Typeface.BOLD)
                setTextColor(badgeTextColor)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(badgeBgColor)
                }
            }
            row.addView(number, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(8) })

            val name = AutoCompleteTextView(activity).apply {
                hint = when {
                    isFirst -> activity.getString(R.string.community_start_stop)
                    isLast -> activity.getString(R.string.community_destination)
                    else -> activity.getString(R.string.community_stop_name)
                }
                threshold = 1
                textSize = 14.5f
                typeface = Typeface.create(fontUi, Typeface.BOLD)
                setSingleLine(true)
                setTextColor(textPrimary)
                setHintTextColor(textMuted)
                setPadding(dp(2), 0, dp(4), 0)
                setBackgroundColor(Color.TRANSPARENT)
                setText(draft.optString("name"), false)
                // Keep the phone-number autofill suggestion out of stop-name fields.
                androidx.core.view.ViewCompat.setImportantForAutofill(this, android.view.View.IMPORTANT_FOR_AUTOFILL_NO)
            }
            val initialLang = draft.optString("language", LanguageHelper.current())
            // Keep each autocomplete row and the text inserted by AutoCompleteTextView to ONE
            // language-specific name. A combined "English • Hindi • Punjabi" label can leak
            // into the route draft when the user taps + or ⋮ before the click callback settles.
            fun suggestionLabel(p: Place, lang: String): String = p.displayName(lang).trim()
            var filteredPlaces = allPlaces.toList()
            // AutoCompleteTextView may put the full multi-language label into the field
            // before onItemClick fires. Ignore that generated label and retain the language
            // inferred from what the user actually typed.
            var selectingSuggestion = false
            var typedLanguage = initialLang
            val adapter = ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, filteredPlaces.map { suggestionLabel(it, initialLang) })
            name.setAdapter(adapter)
            name.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val q = s?.toString().orEmpty()
                    if (selectingSuggestion || q.contains(" • ")) return
                    draft.put("name", q).put("place_id", JSONObject.NULL)
                    typedLanguage = when {
                        q.any { it.code in 0x0A00..0x0A7F } -> "pa"
                        q.any { it.code in 0x0900..0x097F } -> "hi"
                        q.any { it in 'a'..'z' || it in 'A'..'Z' } -> "en"
                        else -> LanguageHelper.current()
                    }
                    draft.put("language", typedLanguage)
                    filteredPlaces = allPlaces.filter { p ->
                        (q.isBlank() || p.names.values.any { it.contains(q, true) }) &&
                            drafts.none { other -> other !== draft && !other.isNull("place_id") && other.optString("place_id") == p.id }
                    }
                    adapter.clear()
                    adapter.addAll(filteredPlaces.map { suggestionLabel(it, typedLanguage) })
                    adapter.notifyDataSetChanged()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
            name.setOnItemClickListener { _, _, pos, _ ->
                val p = filteredPlaces.getOrNull(pos)
                if (p != null) {
                    val lang = typedLanguage
                    val selectedName = p.displayName(lang).trim()
                    selectingSuggestion = true
                    draft.put("place_id", p.id).put("name", selectedName).put("language", lang)
                    name.setText(selectedName, false)
                    name.setSelection(name.text.length)
                    selectingSuggestion = false
                }
            }
            row.addView(name, LinearLayout.LayoutParams(0, dp(44), 1f))

            val time = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(4), dp(5), dp(4))
                val currentTime = timeOf(draft)
                background = GradientDrawable().apply {
                    setColor(if (currentTime.isNullOrBlank()) 0xfff4f8fd.toInt() else 0xffeaf2fb.toInt())
                    setStroke(dp(1), if (currentTime.isNullOrBlank()) 0xffa9c3df.toInt() else 0xffb9cfe6.toInt())
                    cornerRadius = dp(12).toFloat()
                }
                if (currentTime.isNullOrBlank()) {
                    addView(ClockGlyphView(activity), LinearLayout.LayoutParams(dp(27), dp(27)))
                } else {
                    addView(TextView(activity).apply {
                        text = currentTime
                        textSize = 13f
                        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                        gravity = Gravity.CENTER
                        setTextColor(0xff153456.toInt())
                    }, LinearLayout.LayoutParams(-1, -1))
                }
                contentDescription = currentTime ?: activity.getString(R.string.ar_tap_time)
                applyBouncyPress()
                setOnClickListener {
                    val cal = java.util.Calendar.getInstance()
                    val parts = timeOf(draft).orEmpty().split(":")
                    val h = parts.getOrNull(0)?.toIntOrNull() ?: cal.get(java.util.Calendar.HOUR_OF_DAY)
                    val m = parts.getOrNull(1)?.toIntOrNull() ?: cal.get(java.util.Calendar.MINUTE)
                    val dialog = TimePickerDialog(activity, { _, hh, mm ->
                        val value = String.format(Locale.ROOT, "%02d:%02d", hh, mm)
                        val ok = validateTimes?.invoke(timesWith(index, value)) ?: true
                        if (ok) {
                            draft.put("time", value)
                            render()
                        }
                    }, h, m, true)
                    dialog.setButton(DialogInterface.BUTTON_NEUTRAL, activity.getString(R.string.ar_clear)) { _, _ ->
                        draft.put("time", JSONObject.NULL)
                        render()
                    }
                    dialog.show()
                }
            }
            row.addView(time, LinearLayout.LayoutParams(dp(74), dp(44)).apply { marginStart = dp(5) })

            val add = TextView(activity).apply {
                text = "+"
                textSize = 20f
                typeface = Typeface.create(fontUi, Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(activity.getColor(R.color.callBg))
                    cornerRadius = dp(10).toFloat()
                }
                contentDescription = activity.getString(R.string.ar_add_stop_below)
                applyBouncyPress()
                setOnClickListener {
                    // A selected place's canonical single-language name is already in draft.
                    // Never overwrite it with an autocomplete presentation string.
                    if (draft.isNull("place_id")) draft.put("name", name.text.toString().trim())
                    drafts.add(index + 1, newDraft())
                    render()
                }
            }
            row.addView(add, LinearLayout.LayoutParams(dp(34), dp(36)).apply { marginStart = dp(5) })

            val more = TextView(activity).apply {
                text = "⋮"
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(textSecondary)
                contentDescription = activity.getString(R.string.ar_more_stop_actions)
                setOnClickListener { anchor ->
                    if (draft.isNull("place_id")) draft.put("name", name.text.toString().trim())
                    val menu = PopupMenu(activity, anchor)
                    val up = activity.getString(R.string.ar_move_up)
                    val down = activity.getString(R.string.ar_move_down)
                    val remove = activity.getString(R.string.ar_remove_stop)
                    menu.menu.add(up).isEnabled = index > 0
                    menu.menu.add(down).isEnabled = index < drafts.lastIndex
                    menu.menu.add(remove).setIcon(android.R.drawable.ic_menu_delete)
                    menu.setOnMenuItemClickListener { item ->
                        when (item.title.toString()) {
                            up -> if (index > 0) { val v = drafts.removeAt(index); drafts.add(index - 1, v); render() }
                            down -> if (index < drafts.lastIndex) { val v = drafts.removeAt(index); drafts.add(index + 1, v); render() }
                            remove -> if (drafts.size <= minStops) {
                                Toast.makeText(activity, R.string.ar_min_stops, Toast.LENGTH_SHORT).show()
                            } else {
                                val builder = AlertDialog.Builder(activity)
                                    .setTitle(activity.getString(R.string.community_remove_stop_question))
                                    .setNegativeButton(activity.getString(R.string.cancel), null)
                                    .setPositiveButton(activity.getString(R.string.remove)) { _, _ ->
                                        if (index in drafts.indices) { drafts.removeAt(index); render() }
                                    }
                                if (removeWarning != null) builder.setMessage(removeWarning)
                                builder.show()
                            }
                        }
                        true
                    }
                    menu.show()
                }
            }
            row.addView(more, LinearLayout.LayoutParams(dp(28), dp(38)).apply { marginStart = dp(2) })
            box.addView(row, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(4)) })
        }

        val addAtEnd = buildActionButton(activity.getString(R.string.community_add_stop_end)) {
            drafts.add(newDraft())
            render()
        }
        box.addView(addAtEnd, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(10) })
    }
}
