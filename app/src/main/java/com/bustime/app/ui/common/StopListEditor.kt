package com.bustime.app.ui.common

import android.app.TimePickerDialog
import android.content.DialogInterface
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bustime.app.R
import com.bustime.app.models.Place
import com.bustime.app.utils.LanguageHelper
import org.json.JSONObject
import java.util.Locale

/**
 * रूट के पड़ावों की एक जैसी लिस्ट: Route Correction और Add Route दोनों स्क्रीन यही इस्तेमाल करती हैं,
 * इसलिए दोनों हमेशा एक जैसी दिखती और चलती हैं।
 *
 * हर लाइन: नंबर (पहला और आख़िरी हरा) | नाम (सुझावों के साथ) | समय | + (नीचे नया पड़ाव) | ⋮ (ऊपर / नीचे / हटाओ)।
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
    private fun dp(n: Int): Int = (n * activity.resources.displayMetrics.density).toInt()

    fun newDraft(): JSONObject = JSONObject().put("place_id", JSONObject.NULL).put("name", "")
        .put("language", LanguageHelper.current()).put("time", JSONObject.NULL)

    private fun timeOf(d: JSONObject): String? =
        if (d.isNull("time")) null else d.optString("time").trim().takeIf { it.isNotBlank() && it != "null" }

    private fun timesWith(index: Int, value: String): List<String?> =
        drafts.mapIndexed { i, d -> if (i == index) value else timeOf(d) }

    fun render() {
        box.removeAllViews()
        val bg = android.util.TypedValue()
        val fg = android.util.TypedValue()
        activity.theme.resolveAttribute(android.R.attr.colorBackground, bg, true)
        activity.theme.resolveAttribute(android.R.attr.textColorPrimary, fg, true)
        val surfaceColor = bg.data
        val textColor = fg.data
        val night = (activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

        // Compact, theme-aware rows: no large white cards and no four-button toolbar per stop.
        val addStart = Button(activity).apply {
            text = activity.getString(R.string.community_add_start)
            setTextColor(android.graphics.Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.rgb(32, 145, 75))
            setOnClickListener {
                drafts.add(0, newDraft())
                render()
            }
        }
        box.addView(addStart, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(8) })
        drafts.forEachIndexed { index, draft ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(3), dp(2), dp(3), dp(2))
                minimumHeight = dp(54)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(surfaceColor)
                    cornerRadius = dp(7).toFloat()
                }
            }
            val number = TextView(activity).apply {
                text = (index + 1).toString()
                textSize = 13f
                setTextColor(textColor)
                gravity = Gravity.CENTER
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (index == 0 || index == drafts.lastIndex) android.graphics.Color.rgb(44, 115, 83) else android.graphics.Color.rgb(83, 91, 108))
                    cornerRadius = dp(14).toFloat()
                }
                minWidth = dp(28)
                setPadding(dp(5), dp(3), dp(5), dp(3))
            }
            row.addView(number, LinearLayout.LayoutParams(dp(30), dp(28)).apply { marginEnd = dp(5) })

            val name = AutoCompleteTextView(activity).apply {
                hint = if (index == 0) activity.getString(R.string.community_start_stop)
                else if (index == drafts.lastIndex) activity.getString(R.string.community_destination)
                else activity.getString(R.string.community_stop_name)
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
            // The dropdown may show all translations for identification, but the value saved
            // in a stop draft must be only the selected language's name (never the whole label).
            var selectingSuggestion = false
            val adapter = ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, filteredPlaces.map { suggestionLabel(it, initialLang) })
            name.setAdapter(adapter)
            name.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (selectingSuggestion) return
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
                            drafts.none { other -> other !== draft && !other.isNull("place_id") && other.optString("place_id") == p.id }
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
                    val selectedName = p.displayName(lang).trim()
                    selectingSuggestion = true
                    draft.put("place_id", p.id).put("name", selectedName).put("language", lang)
                    name.setText(selectedName, false)
                    name.setSelection(name.text.length)
                    selectingSuggestion = false
                }
            }
            row.addView(name, LinearLayout.LayoutParams(0, dp(44), 1f))

            val time = TextView(activity).apply {
                val t = timeOf(draft).orEmpty()
                text = if (t.isBlank()) "＋ ${activity.getString(R.string.ar_tap_time)}" else t
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(if (t.isBlank()) textColor else android.graphics.Color.rgb(58, 133, 190))
                setPadding(dp(5), dp(4), dp(5), dp(4))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (t.isBlank()) android.graphics.Color.TRANSPARENT else if (night) 0xFF263746.toInt() else 0xFFEAF3FA.toInt())
                    cornerRadius = dp(6).toFloat()
                }
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
                            text = value
                            setTextColor(android.graphics.Color.rgb(58, 133, 190))
                        }
                    }, h, m, true)
                    dialog.setButton(DialogInterface.BUTTON_NEUTRAL, activity.getString(R.string.ar_clear)) { _, _ ->
                        draft.put("time", JSONObject.NULL)
                        render()
                    }
                    dialog.show()
                }
            }
            row.addView(time, LinearLayout.LayoutParams(dp(56), dp(36)).apply { marginStart = dp(3) })

            val add = TextView(activity).apply {
                text = "+"
                textSize = 23f
                gravity = Gravity.CENTER
                setTextColor(android.graphics.Color.WHITE)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.rgb(32, 145, 75))
                    cornerRadius = dp(7).toFloat()
                }
                contentDescription = activity.getString(R.string.ar_add_stop_below)
                setOnClickListener {
                    draft.put("name", name.text.toString().trim())
                    drafts.add(index + 1, newDraft())
                    render()
                }
            }
            row.addView(add, LinearLayout.LayoutParams(dp(34), dp(36)).apply { marginStart = dp(4) })

            val more = TextView(activity).apply {
                text = "⋮"
                textSize = 23f
                gravity = Gravity.CENTER
                setTextColor(textColor)
                contentDescription = activity.getString(R.string.ar_more_stop_actions)
                setOnClickListener { anchor ->
                    draft.put("name", name.text.toString().trim())
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
            row.addView(more, LinearLayout.LayoutParams(dp(28), dp(40)).apply { marginStart = dp(1) })
            box.addView(row, LinearLayout.LayoutParams(-1, dp(58)).apply { setMargins(0, dp(1), 0, dp(1)) })
            val divider = View(activity).apply { setBackgroundColor(if (textColor == android.graphics.Color.WHITE) 0xFF393939.toInt() else 0xFFE0E0E0.toInt()) }
            box.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))
        }
        val addAtEnd = Button(activity).apply {
            text = activity.getString(R.string.community_add_stop_end)
            setTextColor(android.graphics.Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.rgb(32, 145, 75))
            setOnClickListener {
                drafts.add(newDraft())
                render()
            }
        }
        box.addView(addAtEnd, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })
    }
}
